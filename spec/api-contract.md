# Support Ticket Management System API Contract

## 1. Purpose and Authority

This document defines the complete REST API contract for the approved Support
Ticket Management System scope. It is a specification only; it does not define
Java classes, persistence entities, database schema, frontend components, or
tests.

Contract authority, from highest to lowest, is:

1. `spec/requirements.md`
2. `spec/architecture.md`
3. `spec/data-model.md`

No conflict was found among those approved specifications while preparing this
contract. Where a lower-level specification intentionally deferred an API
decision, this document makes that decision and labels it as an API-contract
decision. Backend business and lifecycle rules remain authoritative.

## 2. API Conventions

### 2.1 Base path and media type

- The API base path is `/api`.
- Ticket resources use `/api/tickets`.
- Requests and responses with bodies use JSON.
- The expected media type is `application/json`.
- Endpoint paths use lowercase plural resource names.

### 2.2 Representations

API DTOs are logical request and response representations. They are separate
from persistence entities and do not expose database annotations, key
generation, repository structures, transaction handling, or lifecycle
implementation.

Unknown request fields are not part of the contract and must be rejected with
`400 Bad Request`. This prevents fields such as `status` from being smuggled
into an ordinary ticket update.

### 2.3 Identifier representation

Ticket and Comment identifiers are represented as decimal strings in JSON and
as decimal path segments in URLs.

Examples:

- JSON: `"id": "42"`
- Path: `/api/tickets/42`

This is an **API-contract decision**. The persistence model uses generated
64-bit integers, while decimal strings avoid loss of precision in JavaScript
clients and keep persistence generation mechanics out of the public contract.
Identifiers are opaque to clients: clients may compare and transmit them but
must not infer sequence behavior.

An identifier path segment that cannot represent the selected identifier form
is invalid request input and produces `400 Bad Request`. A well-formed
identifier that does not identify a Ticket produces `404 Not Found`.

### 2.4 Comment timestamp representation

Comment `timestamp` is an RFC 3339 date-time string representing an instant,
normalized to UTC with the `Z` suffix.

Example:

```json
"timestamp": "2026-09-22T15:30:00Z"
```

The API defines no Ticket timestamp. In particular, it does not expose
`createdAt`, `updatedAt`, `resolvedAt`, `closedAt`, or `cancelledAt`.

### 2.5 Assignee representation

Assignee is a String or JSON `null`:

- On creation, omitting `assignee` creates an unassigned Ticket.
- On creation, `"assignee": null` also creates an unassigned Ticket.
- A String value represents the assigned value.
- On PATCH, omitting `assignee` leaves it unchanged.
- On PATCH, `"assignee": null` clears it.

Assignee is not a User object or user identifier. No format, non-blank rule, or
maximum length is added by this contract.

## 3. Resource Representations

### 3.1 Ticket summary

A Ticket summary is returned in collection responses:

| Field | JSON type | Required | Meaning |
| --- | --- | --- | --- |
| `id` | String | Yes | Opaque Ticket identifier |
| `title` | String | Yes | Ticket title |
| `priority` | String enum | Yes | `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL` |
| `assignee` | String or null | Yes | Current optional assignee |
| `status` | String enum | Yes | Current Ticket status |

Description and Comments are omitted from summaries to avoid returning full
Ticket details and Comment collections for every list item.

Example:

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "priority": "HIGH",
  "assignee": "Support Team A",
  "status": "OPEN"
}
```

### 3.2 Ticket resource

A Ticket resource represents Ticket fields without its Comment collection:

| Field | JSON type | Required |
| --- | --- | --- |
| `id` | String | Yes |
| `title` | String | Yes |
| `description` | String | Yes |
| `priority` | String enum | Yes |
| `assignee` | String or null | Yes |
| `status` | String enum | Yes |

Example:

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": "Support Team A",
  "status": "OPEN"
}
```

### 3.3 Comment resource

| Field | JSON type | Required | Meaning |
| --- | --- | --- | --- |
| `id` | String | Yes | Opaque Comment identifier |
| `body` | String | Yes | Comment content |
| `timestamp` | RFC 3339 UTC String | Yes | Instant the Comment was added |

`ticketId` is not repeated because the Comment is nested within a Ticket
resource or created through a Ticket-scoped endpoint. Author information is
not present.

Example:

```json
{
  "id": "84",
  "body": "The issue has been reproduced.",
  "timestamp": "2026-09-22T15:30:00Z"
}
```

### 3.4 Ticket detail

Ticket detail contains all Ticket resource fields plus `comments`:

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": "Support Team A",
  "status": "OPEN",
  "comments": [
    {
      "id": "84",
      "body": "The issue has been reproduced.",
      "timestamp": "2026-09-22T15:30:00Z"
    }
  ]
}
```

`comments` is always present and is an empty array when the Ticket has no
Comments. No ordering guarantee is provided for Comments because no Comment
sorting requirement is approved. Clients must not infer chronological order
from array position.

## 4. Endpoint Summary

| Operation | Method and path | Success |
| --- | --- | --- |
| Create Ticket | `POST /api/tickets` | `201 Created` |
| List/search/filter Tickets | `GET /api/tickets` | `200 OK` |
| Get Ticket details | `GET /api/tickets/{id}` | `200 OK` |
| Partially update Ticket fields | `PATCH /api/tickets/{id}` | `200 OK` |
| Transition Ticket status | `PATCH /api/tickets/{id}/status` | `200 OK` |
| Add Comment | `POST /api/tickets/{id}/comments` | `201 Created` |

Comments are retrieved as part of Ticket details. A separate Comment retrieval
endpoint is not introduced because it is unnecessary for the approved UI
capabilities and would not add required behavior.

## 5. Create Ticket

### 5.1 Request

`POST /api/tickets`

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `title` | String | Yes | Maximum 200 characters |
| `description` | String | Yes | Maximum 5,000 characters |
| `priority` | String enum | Yes | `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL` |
| `assignee` | String or null | No | Optional; no other approved constraint |

The client must not supply `id`, `status`, Comments, or timestamps. The backend
generates the identifier and assigns initial status `OPEN`.

“Required” means the field must be present and non-null. This contract does not
add minimum-length or whitespace-only rejection rules.

Request example:

```json
{
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": null
}
```

### 5.2 Success

- Status: `201 Created`
- Body: Ticket resource

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": null,
  "status": "OPEN"
}
```

No `Location` header requirement is introduced.

### 5.3 Failures

- `400 Bad Request`: Missing or null required field, title over 200
  characters, description over 5,000 characters, invalid Priority, prohibited
  or unknown request field, or malformed JSON.

## 6. List, Search, and Filter Tickets

### 6.1 Request

`GET /api/tickets`

Optional query parameters:

| Parameter | Type | Meaning |
| --- | --- | --- |
| `keyword` | String | Case-insensitive partial match against title or description |
| `status` | String enum | Exact match against an approved Ticket status |

Examples:

- List all: `GET /api/tickets`
- Search: `GET /api/tickets?keyword=payment`
- Filter: `GET /api/tickets?status=OPEN`
- Combine: `GET /api/tickets?keyword=payment&status=OPEN`

Keyword matching uses these semantics:

- Comparison is case-insensitive.
- A partial match is sufficient.
- A Ticket matches when the keyword occurs in either title **or**
  description.

Status filtering accepts exactly one of `OPEN`, `IN_PROGRESS`, `RESOLVED`,
`CLOSED`, or `CANCELLED` and uses exact matching.

When both parameters are present, a Ticket is returned only when:

`keyword match AND exact status match`

Omitting `keyword` means no keyword criterion. Omitting `status` means no
status criterion. If `keyword` is supplied as an empty String, partial-match
semantics apply without adding a new non-empty validation rule; this is
equivalent to no restrictive keyword criterion.

Pagination, sorting, fuzzy matching, relevance ranking, and advanced search
syntax are not supported.

### 6.2 Success

- Status: `200 OK`
- Body: JSON array of Ticket summaries
- No matches: `200 OK` with `[]`

```json
[
  {
    "id": "42",
    "title": "Payment confirmation missing",
    "priority": "HIGH",
    "assignee": null,
    "status": "OPEN"
  },
  {
    "id": "43",
    "title": "Payment receipt unavailable",
    "priority": "MEDIUM",
    "assignee": "Support Team A",
    "status": "OPEN"
  }
]
```

Comments and description are not included in this collection response.
Response ordering is not guaranteed because sorting is outside scope.

### 6.3 Failures

- `400 Bad Request`: Invalid `status` value or otherwise syntactically invalid
  request/query input.

## 7. Get Ticket Details

### 7.1 Request

`GET /api/tickets/{id}`

### 7.2 Success

- Status: `200 OK`
- Body: Ticket detail, including Comments

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": null,
  "status": "OPEN",
  "comments": [
    {
      "id": "84",
      "body": "The issue has been reproduced.",
      "timestamp": "2026-09-22T15:30:00Z"
    }
  ]
}
```

Comment ordering is unspecified.

### 7.3 Failures

- `400 Bad Request`: Malformed identifier.
- `404 Not Found`: Well-formed identifier does not identify a Ticket.

## 8. Update Ticket Fields

### 8.1 Request

`PATCH /api/tickets/{id}`

The request is a partial update and may contain only:

| Field | Type | When supplied |
| --- | --- | --- |
| `title` | String | Replaces title; non-null; maximum 200 characters |
| `description` | String | Replaces description; non-null; maximum 5,000 characters |
| `priority` | String enum | Replaces priority; non-null; approved value only |
| `assignee` | String or null | String replaces assignee; null clears assignee |

Omitted fields remain unchanged. At least one supported field must be present.
An empty JSON object is invalid and produces `400 Bad Request`. This is an
**API-contract decision** needed to ensure PATCH requests express an update; it
does not add a new Ticket-field validation rule.

The request must not contain `id`, `status`, Comments, or timestamps. In
particular, status is never accepted by ordinary PATCH.

Example:

```json
{
  "priority": "CRITICAL",
  "assignee": "Support Team B"
}
```

Clear assignee:

```json
{
  "assignee": null
}
```

### 8.2 Success

- Status: `200 OK`
- Body: Updated Ticket resource

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "CRITICAL",
  "assignee": "Support Team B",
  "status": "OPEN"
}
```

### 8.3 Failures

- `400 Bad Request`: Empty object, invalid supported field value, null for
  title/description/priority, prohibited or unknown field, malformed JSON, or
  malformed identifier.
- `404 Not Found`: Ticket does not exist.
- `409 Conflict`: Ticket is `CLOSED` or `CANCELLED`; persisted fields remain
  unchanged.

The backend checks current persisted status. Frontend controls cannot
authorize an update.

## 9. Transition Ticket Status

### 9.1 Request

`PATCH /api/tickets/{id}/status`

Request fields:

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `targetStatus` | String enum | Yes | Requested target Ticket status |

```json
{
  "targetStatus": "IN_PROGRESS"
}
```

The client sends only the target status. It does not send the current status,
a transition name, or a transition matrix. The backend reads current
persisted status and decides whether the request is allowed.

### 9.2 Allowed transitions

Only these transitions succeed:

- `OPEN → IN_PROGRESS`
- `IN_PROGRESS → RESOLVED`
- `RESOLVED → CLOSED`
- `OPEN → CANCELLED`
- `IN_PROGRESS → CANCELLED`

Every other transition is invalid, including:

- Same-status requests.
- Any transition from `CLOSED`.
- Any transition from `CANCELLED`.
- Any transition between approved statuses not listed above.

### 9.3 Success

- Status: `200 OK`
- Body: Updated Ticket resource

```json
{
  "id": "42",
  "title": "Payment confirmation missing",
  "description": "The payment completed but no confirmation was displayed.",
  "priority": "HIGH",
  "assignee": "Support Team A",
  "status": "IN_PROGRESS"
}
```

### 9.4 Failures

- `400 Bad Request`: Missing/null `targetStatus`, target value outside the
  approved status enum, unknown field, malformed JSON, or malformed
  identifier.
- `404 Not Found`: Ticket does not exist.
- `409 Conflict`: Requested transition is not one of the five allowed
  transitions from the current persisted status.

An invalid transition is never silently accepted or treated as a no-op. A
`409 Conflict` response guarantees that persisted Ticket state is unchanged.

## 10. Add Comment

### 10.1 Request

`POST /api/tickets/{id}/comments`

| Field | Type | Required | Constraints |
| --- | --- | --- | --- |
| `body` | String | Yes | Non-null; no maximum length is specified |

The client must not supply Comment `id`, `ticketId`, `timestamp`, author, or
User information. The backend generates the Comment identifier, associates
the current Ticket, and assigns the timestamp.

```json
{
  "body": "The issue has been reproduced."
}
```

The contract does not add a minimum length or whitespace-only rule.

### 10.2 Success

- Status: `201 Created`
- Body: Comment resource

```json
{
  "id": "84",
  "body": "The issue has been reproduced.",
  "timestamp": "2026-09-22T15:30:00Z"
}
```

### 10.3 Failures

- `400 Bad Request`: Missing/null body, unknown field, malformed JSON, or
  malformed Ticket identifier.
- `404 Not Found`: Ticket does not exist.
- `409 Conflict`: Ticket is `CLOSED` or `CANCELLED`; no Comment is persisted.

Comment editing and deletion are not supported.

## 11. Validation Contract

### 11.1 Request/input validation

Request validation occurs at the API boundary:

- Create title is present, non-null, and no more than 200 characters.
- Create description is present, non-null, and no more than 5,000 characters.
- Create priority is present, non-null, and an approved value.
- PATCH title, when present, is non-null and no more than 200 characters.
- PATCH description, when present, is non-null and no more than 5,000
  characters.
- PATCH priority, when present, is non-null and an approved value.
- Comment body is present and non-null.
- Target status is present, non-null, and an approved status value.
- Request JSON is syntactically valid and contains only fields allowed by the
  operation.

Lengths must conform to the approved maximum limits of 200 characters for
title and 5,000 characters for description. The implementation must apply
these limits consistently with this API contract. No minimum length, trimming,
whitespace-only rejection, assignee format, assignee maximum, or Comment-body
maximum is introduced.

Request-validation failures use `400 Bad Request`.

### 11.2 Business-rule validation

The service/business layer evaluates current persisted state:

- `CLOSED` and `CANCELLED` Tickets reject ordinary field updates.
- `CLOSED` and `CANCELLED` Tickets reject new Comments.

These state conflicts use `409 Conflict`. Selecting `409` for terminal-state
operation conflicts is an **API-contract decision** consistent with their
business-state nature and the error categories requested for this contract.

### 11.3 State-transition validation

After request validation, the service compares current persisted status with
`targetStatus`. Only the five approved transitions succeed. All other
combinations, including same-status requests, use `409 Conflict` and cause no
persistent change.

### 11.4 Persistence constraints

Database nullability, enum-value, identity, and referential constraints
protect persisted integrity but do not replace API or service validation. The
database does not authorize lifecycle transitions.

## 12. Error Contract

All errors governed by this contract use:

| Field | JSON type | Meaning |
| --- | --- | --- |
| `code` | String | Stable machine-readable error code |
| `message` | String | Human-readable description |
| `status` | Integer | HTTP status code |
| `path` | String | Request path, excluding query parameters |

No stack trace, database detail, persistence class, or internal exception name
is exposed.

### 12.1 Validation error

- Status: `400 Bad Request`
- Code: `VALIDATION_ERROR`
- Meaning: The request syntax, identifier form, query value, field set, or
  field value violates this API contract.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "The request contains invalid input.",
  "status": 400,
  "path": "/api/tickets"
}
```

### 12.2 Ticket not found

- Status: `404 Not Found`
- Code: `TICKET_NOT_FOUND`
- Meaning: A well-formed Ticket identifier does not identify a persisted
  Ticket.

```json
{
  "code": "TICKET_NOT_FOUND",
  "message": "The requested ticket was not found.",
  "status": 404,
  "path": "/api/tickets/999"
}
```

### 12.3 Invalid status transition

- Status: `409 Conflict`
- Code: `INVALID_STATUS_TRANSITION`
- Meaning: The target status is an approved status value, but the transition
  from current persisted status is not allowed.

```json
{
  "code": "INVALID_STATUS_TRANSITION",
  "message": "The requested status transition is not allowed.",
  "status": 409,
  "path": "/api/tickets/42/status"
}
```

### 12.4 Terminal Ticket conflict

- Status: `409 Conflict`
- Code: `TERMINAL_TICKET_CONFLICT`
- Meaning: An ordinary field update or Comment addition was requested for a
  `CLOSED` or `CANCELLED` Ticket.

```json
{
  "code": "TERMINAL_TICKET_CONFLICT",
  "message": "The requested operation is not allowed for a terminal ticket.",
  "status": 409,
  "path": "/api/tickets/42/comments"
}
```

Error messages may include safe contextual detail, but clients should branch
on `code` and `status`, not exact message text.

## 13. HTTP Status Code Matrix

| Endpoint | 200 | 201 | 400 | 404 | 409 |
| --- | --- | --- | --- | --- | --- |
| `POST /api/tickets` | — | Created Ticket | Invalid request | — | — |
| `GET /api/tickets` | Summary array | — | Invalid query | — | — |
| `GET /api/tickets/{id}` | Ticket detail | — | Malformed ID | Missing Ticket | — |
| `PATCH /api/tickets/{id}` | Updated Ticket | — | Invalid request | Missing Ticket | Terminal Ticket |
| `PATCH /api/tickets/{id}/status` | Transitioned Ticket | — | Invalid request | Missing Ticket | Invalid transition |
| `POST /api/tickets/{id}/comments` | — | Created Comment | Invalid request | Missing Ticket | Terminal Ticket |

`201 Created` is used when a new Ticket or Comment resource is created.
`200 OK` is used when an existing resource is retrieved or changed. The
contract does not introduce deletion, asynchronous processing, or no-content
success responses.

## 14. API Invariants

1. Backend validation and business enforcement are authoritative.
2. Ordinary `PATCH /api/tickets/{id}` cannot accept or change status.
3. Status changes occur only through
   `PATCH /api/tickets/{id}/status`.
4. Only the five approved lifecycle transitions succeed.
5. Invalid and same-status transitions return `409 Conflict`.
6. A rejected transition does not modify persisted Ticket state.
7. `CLOSED` and `CANCELLED` Tickets reject title, description, priority, and
   assignee updates.
8. `CLOSED` and `CANCELLED` Tickets reject new Comments.
9. API request validation does not replace service-layer lifecycle
   enforcement.
10. Keyword search is case-insensitive partial matching against title or
    description.
11. Combined keyword and status filtering requires both conditions to match.
12. Status filtering is an exact match.
13. API DTOs do not expose persistence entities or database mechanics.
14. Comment responses contain no author or User information.
15. Ticket responses contain no timestamp.

## 15. API Security Scope

No secret may be committed to the repository. API implementation must obtain
any required environment-specific secret through secure external
configuration.

Authentication, authorization, Users, and roles are outside the approved
scope. This API contract defines no credentials, identity headers, sessions,
tokens, permissions, or access-control behavior.

## 16. API Contract Decisions

### API-001 — Conventional resource paths

- **Decision:** Use `/api/tickets`, nested Ticket operations, and lowercase
  plural resource names.
- **Classification:** API-contract choice.
- **Rationale:** Provides a small consistent REST surface without adding
  capabilities.

### API-002 — Comments embedded in Ticket details

- **Decision:** Include Comments in Ticket detail and do not add a separate
  Comment retrieval endpoint.
- **Classification:** API-contract choice supporting FR-003 and FR-008.
- **Rationale:** Satisfies the Ticket-details UI with the smallest coherent API
  and avoids an unnecessary operation.

### API-003 — No Comment ordering guarantee

- **Decision:** Comment array order is unspecified.
- **Classification:** Preservation of an approved data-model deferral.
- **Rationale:** No sorting requirement is approved.

### API-004 — Decimal String identifiers

- **Decision:** Represent 64-bit persistence identifiers as decimal strings in
  JSON.
- **Classification:** API-contract choice.
- **Rationale:** Avoids JavaScript precision loss and hides generation
  mechanics.

### API-005 — RFC 3339 UTC Comment timestamp

- **Decision:** Represent Comment timestamp as an RFC 3339 UTC instant with
  `Z`.
- **Classification:** API-contract choice implementing BR-005 and the data
  model timestamp decision.
- **Rationale:** Provides an unambiguous interoperable instant.

### API-006 — PATCH requires a field

- **Decision:** Reject an empty ordinary PATCH object with `400 Bad Request`.
- **Classification:** API-contract choice.
- **Rationale:** A partial-update request must express at least one approved
  field change.

### API-007 — Terminal operation conflicts

- **Decision:** Return `409 Conflict` for an ordinary update or Comment
  addition prohibited by current terminal status.
- **Classification:** API-contract choice supporting BR-018 and BR-019.
- **Rationale:** The request is structurally valid but conflicts with current
  persisted business state.

### API-008 — Minimal collection response

- **Decision:** Return an unwrapped array of Ticket summaries without
  description or Comments.
- **Classification:** API-contract choice.
- **Rationale:** Supports listing, searching, and filtering without inventing
  pagination metadata or loading every Comment collection.

## 17. Requirement Traceability

| API operation or concern | Requirements | Architecture | Data model |
| --- | --- | --- | --- |
| Create Ticket | FR-001, BR-002–BR-004, VR-001–VR-006, PR-001, AC-001 | Sections 2, 3, 6, 9; AD-001, AD-006 | Ticket entity; DM-001, DM-002, DM-004, DM-005 |
| List Tickets | FR-002, FR-013, TC-004, AC-002 | Sections 2, 4, 9 | Ticket summary fields |
| Get Ticket details and Comments | FR-003, FR-008, BR-005, AC-003, AC-006 | Sections 4 and 9 | Ticket/Comment relationship; DM-003 |
| Update Ticket fields | FR-004–FR-007, BR-018, BR-020, VR-002–VR-006, VR-009, TC-009, AC-004–AC-005 | Sections 3, 6, 9; AD-003 | Ticket mutability; terminal restrictions |
| Search Tickets | FR-009, BR-006, AC-007 | Sections 3.3, 4, 9 | Searchable title/description; DM-008 |
| Filter Tickets | FR-010, BR-007, AC-008 | Sections 3.3, 4, 9 | Status index; DM-008 |
| Combined search/filter | FR-011, BR-008, AC-009 | Sections 3.3, 4, 9 | Query fields; DM-008 |
| Transition status | FR-012, BR-009–BR-017, BR-021, VR-008, ER-003, PR-005, TC-010, AC-010–AC-013 | Sections 5, 6.3, 8.2, 9; AD-002, AD-003, AD-007 | Current status; DM-004, DM-010 |
| Add Comment | FR-008, BR-005, BR-019, VR-007, VR-009, PR-001, AC-006, AC-014 | Sections 4, 6.2, 9 | Comment entity; DM-003, DM-007 |
| Request validation | VR-001–VR-009, ER-002, ER-006, AC-016 | Sections 3.1 and 6; AD-002 | Nullability and persistence constraints |
| Missing Ticket error | ER-001, AC-017 | Sections 3.6 and 7 | Ticket identity |
| Structured errors | ER-002–ER-005, AC-018–AC-019 | Sections 3.6 and 7; AD-008 | No persistence representation exposed |
| PostgreSQL durability | PR-001, PR-002, PR-004, AC-015 | Section 8; AD-004 | DM-009 |
| H2 integration behavior | PR-003, TC-003, TC-011, AC-020–AC-021 | Section 10; AD-005 | DM-009 |
| REST and DTO boundary | FR-013, TC-004, ER-004 | Sections 3.5 and 9; AD-006 | Logical representations only |
| No committed secrets | TC-008, AC-022 | Section 11 | No data-model secret fields |

Every approved backend capability is represented by an endpoint or by query
semantics on the Ticket collection endpoint.

## 18. Explicit Non-Goals

This API does not provide:

- Ticket deletion.
- Comment retrieval separate from Ticket details.
- Comment editing or deletion.
- Authentication or authorization.
- Users or roles.
- Attachments.
- Pagination or sorting.
- Audit or transition history.
- Notifications.
- Advanced search, fuzzy search, relevance ranking, or external search
  infrastructure.

## 19. Resolved Assumptions and Remaining Questions

### 19.1 Decisions resolved by this contract

- Exact endpoint paths and HTTP methods.
- Decimal String representation of identifiers.
- RFC 3339 UTC representation of Comment timestamp.
- Comments embedded in Ticket detail with no ordering guarantee.
- Minimal Ticket summaries for collection responses.
- Empty PATCH rejection.
- Null assignee clearing semantics.
- `409 Conflict` for terminal-state operation conflicts.
- Exact query parameter names `keyword` and `status`.
- Exact success statuses and baseline error codes.

### 19.2 Details deferred to implementation

The following do not change observable contract semantics and may be selected
during implementation:

- Java DTO class names and package structure.
- Validation framework annotations.
- Internal exception class names.
- Controller and service method names.
- JSON serialization library configuration that preserves this contract.
- Repository query implementation used to achieve required search semantics.
- Internal timestamp clock abstraction.

### 19.3 Questions requiring human approval

None. All decisions necessary to implement the approved API surface are
specified. Any future request for pagination, sorting, authentication,
additional fields, additional endpoints, or new error semantics requires an
approved specification change.

## 20. Self-Review Record

The contract was checked against the approved specifications for:

- Status mutation only through the dedicated status operation.
- Exactly five allowed transitions and rejection of every other transition.
- Unchanged persisted state after transition rejection.
- Terminal-state update and Comment restrictions.
- Case-insensitive partial title/description search using OR semantics.
- Combined keyword/status filtering using AND semantics.
- `400`, `404`, and `409` distinctions.
- Absence of fields not present in the data model.
- Absence of Ticket timestamps and Comment author fields.
- Separation of DTOs from persistence entities.
- Absence of pagination, sorting, deletion, authentication, Users, roles,
  attachments, audit history, notifications, and external search systems.

No conflict with the approved requirements, architecture, or data model was
identified.
