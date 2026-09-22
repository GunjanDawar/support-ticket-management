# Support Ticket Management System UI Flow Specification

## 1. Purpose and Authority

This document defines the approved user-facing behavior and flows for the
Support Ticket Management System. It resolves UI-specific details deferred by
earlier specifications without changing backend validation, lifecycle rules,
API contracts, or product scope.

Authority, from highest to lowest, is:

1. `spec/requirements.md`
2. `spec/architecture.md`
3. `spec/data-model.md`
4. `spec/api-contract.md`
5. `spec/state-machine.md`
6. This UI flow specification

The UI must use the REST API and must never access PostgreSQL or H2 directly.
Backend validation and service-layer lifecycle enforcement are authoritative.
The UI may hide or disable actions based on the status it most recently
received for usability, but that state may be stale. The backend may reject an
operation after evaluating current persisted state, and the UI must handle
that response.

No frontend framework, component implementation, styling system, or source
code is defined here.

## 2. UI Scope

The minimum UI supports:

1. Listing Tickets.
2. Searching Tickets by keyword.
3. Filtering Tickets by status.
4. Combining keyword search and status filtering.
5. Creating a Ticket.
6. Viewing Ticket details.
7. Updating title.
8. Updating description.
9. Updating priority.
10. Updating or clearing assignee.
11. Changing Ticket status through approved lifecycle actions.
12. Adding Comments.
13. Displaying meaningful errors.
14. Displaying loading, empty, and no-result states where applicable.

The UI consists of three primary views:

1. Ticket List View.
2. Create Ticket View.
3. Ticket Detail View.

Editing, status transition, and Comment submission are action flows within the
Ticket Detail context rather than additional application sections.

The UI does not add authentication, User management, roles, dashboards,
notifications, attachments, deletion, Comment editing, pagination, sorting,
audit history, transition history, advanced search, analytics, or unrelated
navigation.

## 3. UI Views and Action Flows

### 3.1 Ticket List View

#### Purpose

The Ticket List View is the entry view for browsing, searching, and filtering
Tickets and for navigating to Ticket creation or details.

#### Displayed information

Each Ticket summary displays only:

- `id`
- `title`
- `priority`
- `assignee`
- `status`

An absent assignee is displayed as a clear unassigned value. Description and
Comments are not displayed in the list.

#### User actions

- Navigate to Create Ticket.
- Open a Ticket's detail view.
- Enter and apply a keyword.
- Select and apply a status filter.
- Apply keyword and status together.
- Clear search/filter criteria.

#### API interaction

The view uses `GET /api/tickets` with optional `keyword` and `status` query
parameters. The UI sends criteria to the backend and renders returned
summaries. It does not independently search or filter a previously loaded
collection when the API is available.

The UI provides no pagination or sorting controls and makes no ordering
guarantee.

### 3.2 Create Ticket View

#### Form fields

| Field | UI control responsibility | Required |
| --- | --- | --- |
| Title | Accept a String and communicate the 200-character maximum | Yes |
| Description | Accept a String and communicate the 5,000-character maximum | Yes |
| Priority | Offer only `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL` | Yes |
| Assignee | Accept an optional String | No |

The form does not expose:

- Ticket `id`
- Status
- Comments
- Any timestamp

The user cannot choose initial status.

#### Submission flow

1. The user enters the required values and optionally an assignee.
2. The UI applies only approved client-side validation.
3. The UI submits `POST /api/tickets`.
4. During submission, duplicate create submissions are prevented.
5. On `201 Created`, the backend response contains the generated identifier
   and status `OPEN`.
6. The UI navigates to the created Ticket's detail view and loads its current
   backend state.
7. On failure, the user remains in the creation context and sees a meaningful
   error without internal implementation detail.

Navigating to Ticket details after success is **UI decision UI-D-002**. It gives
the user immediate confirmation and access to approved follow-up actions.

### 3.3 Ticket Detail View

#### Displayed information

The Ticket Detail View displays:

- `id`
- `title`
- `description`
- `priority`
- `assignee`
- `status`
- Comments

Each Comment displays:

- `body`
- `timestamp`

The UI does not display Comment author/User information or any Ticket
timestamp.

The view uses `GET /api/tickets/{id}`. The `comments` array may be empty.
Comment order is rendered as received, but the UI must not label or imply that
the array is chronological because the API provides no ordering guarantee.

#### Available actions

Subject to current displayed status, the detail context provides:

- Enter or exit ordinary edit mode.
- Submit approved Ticket field changes.
- Request an available status transition.
- Add a Comment.
- Return to the Ticket List.

The UI displays current backend data after successful mutations.

### 3.4 Edit Ticket Flow

The ordinary edit flow supports only:

- Title.
- Description.
- Priority.
- Assignee.

It uses `PATCH /api/tickets/{id}`.

#### PATCH construction

- Only changed supported fields need to be sent.
- Omitted fields remain unchanged.
- A String `assignee` replaces the current value.
- `"assignee": null` clears the assignee.
- The UI must not submit an empty object.
- The UI must never include `id`, `status`, Comments, or timestamps.

If the user enters edit mode and makes no change, the UI does not submit a
PATCH request.

#### Success and failure

- On `200 OK`, the UI exits edit mode, displays the returned Ticket fields,
  and refreshes the Ticket detail to obtain current backend state.
- On `400 VALIDATION_ERROR`, edit mode remains available so the user can
  correct input.
- On `404 TICKET_NOT_FOUND`, the UI displays the missing-Ticket behavior in
  Section 7.
- On `409 TERMINAL_TICKET_CONFLICT`, the UI displays a terminal-state error
  and refreshes the Ticket detail.

For a displayed `CLOSED` or `CANCELLED` Ticket, edit controls are hidden or
disabled. This is a usability behavior only; the backend still enforces the
rule.

### 3.5 Status Transition Flow

Status transition is separate from ordinary edit mode and uses:

`PATCH /api/tickets/{id}/status`

The UI sends only:

```json
{
  "targetStatus": "..."
}
```

It does not send current status as an authoritative value.

Available actions are derived from the currently displayed status:

| Current status | UI action label | API target |
| --- | --- | --- |
| `OPEN` | Start / In Progress | `IN_PROGRESS` |
| `OPEN` | Cancel | `CANCELLED` |
| `IN_PROGRESS` | Resolve | `RESOLVED` |
| `IN_PROGRESS` | Cancel | `CANCELLED` |
| `RESOLVED` | Close | `CLOSED` |
| `CLOSED` | None | — |
| `CANCELLED` | None | — |

These are the only lifecycle actions presented. The labels express the five
approved state-machine transitions and do not add a new transition.

#### Success

1. The UI prevents accidental duplicate transition submission while the
   request is active.
2. On `200 OK`, the UI displays the returned status.
3. The UI reloads Ticket detail so all controls reflect current backend state.

#### Invalid transition or stale state

On `409 INVALID_STATUS_TRANSITION`, the UI:

1. Displays a lifecycle-specific message such as, “This status change is no
   longer allowed. The ticket may have changed.”
2. Does not represent the requested transition as successful.
3. Reloads the Ticket through `GET /api/tickets/{id}`.
4. Recomputes visible actions from the refreshed status.

Frontend action visibility is advisory. The backend evaluates persisted state
and is the only lifecycle authority.

### 3.6 Comment Flow

The Ticket Detail View provides a Comment form containing one field:

- Body.

The form does not request or submit:

- Comment `id`
- `ticketId`
- Timestamp
- Author
- User information

The UI sends:

`POST /api/tickets/{id}/comments`

#### Success

1. The UI prevents duplicate Comment submission while the request is active.
2. On `201 Created`, the backend returns the new Comment.
3. The UI clears the submitted form after success.
4. The UI reloads Ticket detail and renders the Comment array as returned.

Reloading rather than assigning an assumed array position preserves the API's
unspecified Comment ordering.

#### Terminal Ticket behavior

For displayed `CLOSED` or `CANCELLED` status, Comment input and submission are
hidden or disabled. If the backend returns
`409 TERMINAL_TICKET_CONFLICT`, the UI displays a meaningful terminal-state
message and refreshes Ticket detail.

Comment editing and deletion are not exposed.

## 4. Navigation Flow

The minimum navigation paths are:

- `Ticket List → Create Ticket`
- `Ticket List → Ticket Details`
- `Create Ticket → Ticket Details` after successful creation
- `Ticket Details → Edit Ticket flow`
- `Ticket Details → Status Transition flow`
- `Ticket Details → Add Comment flow`
- `Ticket Details → Ticket List`

After successful create, the UI navigates to Ticket details. After successful
update, transition, or Comment creation, the UI remains in the Ticket detail
context and refreshes current backend data.

No additional application section or unrelated navigation is introduced.

## 5. Search and Filter Flow

### 5.1 Keyword search

The user enters a keyword and applies the search. The UI sends it as the
`keyword` query parameter.

The backend semantics are:

- Case-insensitive matching.
- Partial matching.
- A match in title **or** description is sufficient.

The UI must not reinterpret the query as exact matching, case-sensitive
matching, title-only matching, or client-side filtering.

### 5.2 Status filter

The filter offers:

- All.
- `OPEN`.
- `IN_PROGRESS`.
- `RESOLVED`.
- `CLOSED`.
- `CANCELLED`.

“All” means no status filter and causes the UI to omit the `status` query
parameter. A selected status is sent exactly as the `status` query parameter.

### 5.3 Combined search and filter

When keyword and status are both applied, the UI calls:

`GET /api/tickets?keyword=...&status=...`

The backend returns only Tickets satisfying:

`keyword match AND exact status match`

The UI preserves both controls' values while displaying combined results.

### 5.4 Clearing and empty results

Clearing keyword or selecting “All” removes that parameter from the next
request. Clearing both loads `GET /api/tickets`.

An empty array is a successful result. The UI displays “No tickets found” or
equivalent meaningful no-result text. It does not display an API error.

No fuzzy search, relevance ranking, sorting, pagination, advanced syntax, or
external search behavior is exposed.

## 6. Form Validation Behavior

Client-side validation mirrors only approved API constraints:

### 6.1 Create and edit

- Title is required for creation.
- Title has a maximum of 200 characters.
- Description is required for creation.
- Description has a maximum of 5,000 characters.
- Priority is required for creation and limited to `LOW`, `MEDIUM`, `HIGH`,
  and `CRITICAL`.
- On PATCH, a supplied title, description, or priority must satisfy the same
  applicable constraint.
- Assignee is optional and has no added format or length constraint.
- Setting assignee to no value sends explicit JSON `null` when clearing an
  existing assignment.

### 6.2 Comment

- Body is required.
- No maximum Comment-body length is introduced.
- No whitespace-only rule is introduced.

### 6.3 Status

- A selected target must be one of the five approved states.
- The UI offers only target values represented in Section 3.5.
- Actual transition validity is determined by the backend using persisted
  current state.

Client-side validation is for prompt user feedback only. Every request remains
subject to backend request, business-rule, and lifecycle validation. The UI
does not add minimum lengths, trimming requirements, whitespace restrictions,
assignee formats, or other frontend-only business rules.

## 7. Error Handling

The UI consumes the API's structured error fields:

- `code`
- `message`
- `status`
- `path`

It presents safe, meaningful text and never exposes stack traces, database
details, internal exception names, or backend implementation detail.

### 7.1 `400 VALIDATION_ERROR`

- Display a user-friendly validation message.
- For form submissions, keep the user's entered values.
- Associate the error with the relevant form or operation where the failing
  input can be identified safely.
- Do not claim that the operation succeeded.

The approved error contract has no field-error collection, so the UI must not
depend on undocumented field-level error data.

### 7.2 `404 TICKET_NOT_FOUND`

- Display “Ticket not found” or equivalent meaningful text.
- In a detail context, provide a route back to the Ticket List.
- Do not display an empty Ticket as though loading succeeded.

### 7.3 `409 INVALID_STATUS_TRANSITION`

- Display “This status change is no longer allowed. The ticket may have
  changed,” or equivalent lifecycle-specific text.
- Reload Ticket detail.
- Recompute status actions from refreshed backend state.

### 7.4 `409 TERMINAL_TICKET_CONFLICT`

- Explain that the Ticket is already closed or cancelled and the requested
  edit or Comment operation is not allowed.
- Do not retry automatically or attempt to bypass the conflict.
- Reload Ticket detail to update visible controls.

### 7.5 Network, server, and unexpected failures

- Display a generic user-friendly failure message.
- Keep the current UI context where practical so the user does not lose
  entered form data.
- Do not expose technical internals.
- Do not treat an uncertain mutation result as confirmed success.

No new API error code is introduced.

## 8. Loading and Submission States

| Operation | Required UI behavior |
| --- | --- |
| Load Ticket list | Show a clear list-loading state until success or failure |
| Load Ticket details | Show a clear detail-loading state until success or failure |
| Submit creation | Indicate progress and prevent duplicate create submission |
| Submit update | Indicate progress and prevent duplicate update submission |
| Submit status transition | Indicate progress and prevent duplicate transition submission |
| Submit Comment | Indicate progress and prevent duplicate Comment submission |

Only the controls associated with the active mutation need to be blocked.
Loading and disabled states must remain understandable without prescribing a
specific component library or animation.

If a load fails, the loading indicator ends and the applicable error state is
shown.

## 9. Empty and Initial States

### 9.1 Empty Ticket List

When `GET /api/tickets` returns `[]` without search/filter criteria:

- Display an empty-list message.
- Keep the Create Ticket action available.
- Do not present the empty list as an error.

### 9.2 No Search or Filter Results

When a query returns `[]`:

- Display “No tickets found” or equivalent no-result text.
- Preserve active search/filter controls so the user can adjust or clear them.
- Do not present the result as an API error.

### 9.3 Ticket with No Comments

When Ticket detail contains `"comments": []`:

- Display “No comments yet” or equivalent text.
- Keep Comment input available unless status is `CLOSED` or `CANCELLED`.
- Do not imply that Comment loading failed.

### 9.4 Initial form states

- Create Ticket begins with empty title and description, no assignee, and no
  client-selectable status.
- Priority requires a user-selected approved value; this specification does
  not invent a default priority.
- Comment input begins empty.

## 10. Terminal Ticket UI Behavior

For `CLOSED` and `CANCELLED` Tickets:

- No status transition control is shown.
- Title, description, priority, and assignee edit controls are hidden or
  disabled.
- Comment input and submission are hidden or disabled.
- Existing Ticket fields and Comments remain viewable.

When controls are disabled rather than hidden, the UI should communicate that
the Ticket's terminal status makes the operation unavailable.

These are UI-level usability behaviors only. The backend remains
authoritative. The UI must handle backend rejection even when an action
appeared available based on previously loaded state.

No reopen, restore, archive, or deletion action is provided.

## 11. Status Action Mapping

| Current status | Available UI action | API target |
| --- | --- | --- |
| `OPEN` | Start / In Progress | `IN_PROGRESS` |
| `OPEN` | Cancel | `CANCELLED` |
| `IN_PROGRESS` | Resolve | `RESOLVED` |
| `IN_PROGRESS` | Cancel | `CANCELLED` |
| `RESOLVED` | Close | `CLOSED` |
| `CLOSED` | None | — |
| `CANCELLED` | None | — |

No Reopen, Reassign, Archive, Restore, or other lifecycle action is introduced.
Assignee editing remains an ordinary approved Ticket field update and is not a
lifecycle action.

## 12. API Interaction Summary

| UI action | API |
| --- | --- |
| Load Ticket list | `GET /api/tickets` |
| Search/filter | `GET /api/tickets?keyword=...&status=...` |
| Create Ticket | `POST /api/tickets` |
| Load Ticket details | `GET /api/tickets/{id}` |
| Update Ticket fields | `PATCH /api/tickets/{id}` |
| Change status | `PATCH /api/tickets/{id}/status` |
| Add Comment | `POST /api/tickets/{id}/comments` |

There are seven UI-to-API interaction types. Search-only and filter-only calls
use the same collection endpoint with only the applicable query parameter.

No UI operation directly accesses PostgreSQL, H2, a repository, or a
persistence entity.

## 13. UI State Consistency

Displayed frontend state is a snapshot and is never lifecycle authority.

Representative stale-state flow:

1. The UI loads and displays a Ticket as `OPEN`.
2. Another request or process changes the persisted backend status.
3. The user selects Start / In Progress based on the stale display.
4. The UI requests target `IN_PROGRESS`.
5. The backend evaluates the actual persisted current status.
6. If the pair is no longer allowed, the backend returns
   `409 INVALID_STATUS_TRANSITION`.
7. The UI displays the lifecycle error.
8. The UI reloads `GET /api/tickets/{id}`.
9. The UI renders current state and recomputes available controls.

The same refresh principle applies when an edit or Comment request receives
`409 TERMINAL_TICKET_CONFLICT`.

The UI does not optimistically display a mutation as final before backend
success.

## 14. Testable UI Behaviors

- **UI-INV-001:** Ticket List displays `id`, title, priority, assignee, and
  status for each returned summary.
- **UI-INV-002:** Ticket List does not display description or Comments.
- **UI-INV-003:** Create requires title, description, and priority and allows
  optional assignee.
- **UI-INV-004:** A successfully created Ticket is shown with status `OPEN`.
- **UI-INV-005:** Keyword search uses backend case-insensitive partial matching
  over title or description.
- **UI-INV-006:** Status filtering uses exact status.
- **UI-INV-007:** Combined keyword and status filtering requires both
  conditions.
- **UI-INV-008:** Ticket detail displays approved Ticket fields and Comments.
- **UI-INV-009:** An empty Comment array displays a non-error empty state.
- **UI-INV-010:** Edit requests contain only changed title, description,
  priority, or assignee fields.
- **UI-INV-011:** Ordinary edit cannot submit status.
- **UI-INV-012:** Status controls expose exactly the five approved transitions.
- **UI-INV-013:** Invalid transition conflict displays a meaningful error and
  refreshes Ticket state.
- **UI-INV-014:** Terminal Tickets expose no status, edit, or Comment action.
- **UI-INV-015:** `409 TERMINAL_TICKET_CONFLICT` is displayed meaningfully and
  causes state refresh.
- **UI-INV-016:** `404 TICKET_NOT_FOUND` displays missing-Ticket behavior.
- **UI-INV-017:** `400 VALIDATION_ERROR` displays meaningful validation
  feedback.
- **UI-INV-018:** Empty list/search responses display non-error empty states.
- **UI-INV-019:** No UI operation accesses a database directly.
- **UI-INV-020:** No unsupported product capability is exposed.
- **UI-INV-021:** Client controls do not replace backend validation.

These behaviors are test targets for a later test strategy; they are not test
implementation.

## 15. Accessibility and Usability Baseline

- Form fields have visible, understandable labels.
- Required fields are communicated to users.
- Buttons and actions have clear names.
- Keyboard and other standard interaction methods can identify and activate
  available controls.
- Error messages are visible and associated with the relevant form or
  operation where practical.
- Loading and submission states communicate that work is in progress.
- Disabled actions communicate why they are unavailable where practical.
- Status is communicated with text and not solely through color.

This is a modest usability baseline, not a separate accessibility standard.
No CSS framework, component library, or frontend technology beyond the
approved frontend constraint is prescribed.

## 16. Explicit Non-Goals

The UI does not provide:

- Authentication.
- Authorization.
- Users or roles.
- Dashboards.
- Analytics.
- Notifications.
- Attachments.
- Ticket deletion.
- Comment editing or deletion.
- Pagination.
- Sorting.
- Audit history.
- Transition history.
- Advanced or fuzzy search.
- External search infrastructure.
- Lifecycle actions beyond the five approved transitions.
- Ticket or Comment author information.

## 17. Requirement Traceability

| UI flow or behavior | Requirements | Architecture | API contract | State machine |
| --- | --- | --- | --- | --- |
| Ticket List View | FR-002, AC-002 | Section 4 | Sections 3.1 and 6; API-008 | — |
| Create Ticket View | FR-001, BR-002–BR-004, VR-002–VR-006, AC-001, AC-016 | Section 4 | Section 5 | SM-001, INV-001 |
| Ticket Detail View | FR-003, FR-008, BR-005, AC-003, AC-006 | Section 4 | Sections 3.3–3.4 and 7; API-002, API-003 | — |
| Edit Ticket flow | FR-004–FR-007, BR-018, BR-020, VR-002–VR-006, VR-009, AC-004–AC-005, AC-014 | Sections 4 and 6.2; AD-002, AD-003 | Section 8; API-006, API-007 | SM-010, INV-008–INV-009 |
| Status transition flow | FR-012, BR-009–BR-017, BR-021, VR-008, AC-010–AC-013 | Sections 4 and 5; AD-002, AD-003 | Section 9 | SM-002–SM-010, INV-002–INV-007, INV-012–INV-013 |
| Comment flow | FR-008, BR-005, BR-019, VR-007, VR-009, AC-006, AC-014 | Sections 4 and 6.2; AD-002 | Section 10; API-007 | INV-010 |
| Keyword search | FR-009, BR-006, AC-007 | Section 4 | Section 6 | — |
| Status filter | FR-010, BR-007, AC-008 | Section 4 | Section 6 | — |
| Combined search/filter | FR-011, BR-008, AC-009 | Section 4 | Section 6 | — |
| Structured error display | ER-001–ER-005, AC-017–AC-019 | Sections 4 and 7; AD-008 | Sections 12 and 13 | INV-013–INV-015 |
| Client-side validation | VR-002–VR-007, ER-006, AC-016 | Sections 4 and 6 | Section 11 | INV-011 |
| Terminal UI restrictions | BR-016, BR-018–BR-019, VR-009, AC-014 | Sections 4, 5, and 6.2 | Sections 8.3, 10.3, and 14; API-007 | SM-005–SM-006, INV-005–INV-006, INV-009–INV-010 |
| Backend authority and stale-state recovery | VR-001, VR-008–VR-009, ER-006, AC-012–AC-013 | AD-002, AD-007 | Sections 9.4, 11, and 14 | SM-009, INV-007, INV-011 |
| UI/API boundary | FR-013, TC-004–TC-005 | Sections 2 and 4; AD-006 | Sections 2–3 | — |
| No committed secrets | TC-008, AC-022 | Section 11 | Section 15 | — |

The `UI-INV-*` IDs in this document identify testable UI behaviors; they are
not new product requirements.

## 18. Resolved UI Decisions

### UI-D-001 — Three primary views

- **Decision:** Use Ticket List, Create Ticket, and Ticket Detail as the three
  primary views.
- **Classification:** UI-flow decision.
- **Rationale:** They cover all approved UI capabilities without unrelated
  application sections.

### UI-D-002 — Navigate to details after creation

- **Decision:** After successful creation, navigate to and load the new Ticket
  detail.
- **Classification:** UI-flow decision.
- **Rationale:** Confirms backend-assigned identity and `OPEN` status and keeps
  the user in the relevant Ticket context.

### UI-D-003 — Detail-context action flows

- **Decision:** Editing, status changes, and Comment submission occur within
  the Ticket detail context.
- **Classification:** UI-flow decision.
- **Rationale:** Each operation acts on one Ticket and does not require a new
  product area.

### UI-D-004 — Backend-driven list queries

- **Decision:** Submit keyword and status criteria to the collection API rather
  than filtering a locally cached collection.
- **Classification:** Requirement-driven UI-flow decision.
- **Rationale:** Preserves BR-006 through BR-008 semantics and current backend
  data.

### UI-D-005 — Terminal controls

- **Decision:** Hide or disable mutation controls for a displayed terminal
  Ticket while still handling backend conflicts.
- **Classification:** UI-flow usability decision.
- **Rationale:** Avoids presenting known-invalid actions without treating the
  UI as authorization.

### UI-D-006 — Refresh after mutations and state conflicts

- **Decision:** Reload Ticket detail after successful mutations and after
  lifecycle/terminal conflicts.
- **Classification:** UI-flow consistency decision.
- **Rationale:** Aligns controls and displayed data with authoritative backend
  state.

### UI-D-007 — Explicit empty states

- **Decision:** Distinguish an empty Ticket list, no query results, and no
  Comments from errors.
- **Classification:** UI-flow usability decision.
- **Rationale:** The API represents each as a successful empty collection.

### UI-D-008 — Meaningful operation-level errors

- **Decision:** Present safe messages in the current operation context and
  preserve editable input when practical.
- **Classification:** Requirement-driven UI-flow decision.
- **Rationale:** Implements ER-005 without depending on undocumented error
  fields.

## 19. Remaining Questions

No unresolved UI questions remain for the approved scope.

Exact visual layout, component names, routing library, state-management
mechanism, CSS approach, and implementation framework within the approved
frontend constraint are implementation details. They must preserve this flow
and must not add product capabilities.

## 20. Self-Review

This specification was checked to confirm:

1. No frontend or backend code is defined.
2. All approved UI capabilities are covered.
3. Every referenced API endpoint exists in `spec/api-contract.md`.
4. Keyword matching is title **or** description, case-insensitive, and partial.
5. Combined keyword/status matching uses **AND** semantics.
6. Exactly five lifecycle actions map to the five allowed transitions.
7. No reopening action exists.
8. `CLOSED` and `CANCELLED` expose no outgoing status action.
9. Ordinary edit cannot modify status.
10. Terminal Tickets reject field updates and Comments.
11. Backend service-layer decisions remain authoritative.
12. Invalid transitions map to `409 INVALID_STATUS_TRANSITION`.
13. Terminal operation conflicts map to
    `409 TERMINAL_TICKET_CONFLICT`.
14. Invalid input maps to `400 VALIDATION_ERROR`.
15. A missing Ticket maps to `404 TICKET_NOT_FOUND`.
16. Empty collections are handled as successful non-error states.
17. Stale lifecycle conflicts cause an error display and backend refresh.
18. No unsupported product capability is introduced.
