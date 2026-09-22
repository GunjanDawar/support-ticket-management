# Support Ticket Management System Requirements Specification

## 1. Purpose

This document defines the requirements for the Support Ticket Management
System. It is the requirements baseline for later specification,
implementation, testing, review, and remediation work.

Requirement IDs in this document are stable references for downstream
specifications, implementation tasks, and tests.

## 2. Authority and Scope

The assignment brief is the authoritative source for this specification.
The decisions finalized during requirement analysis refine ambiguities in that
brief and are incorporated where applicable.

This document specifies required behavior and constraints. It does not define
the system architecture, detailed data model, complete API contract, standalone
state-machine specification, UI flow, or test strategy. Those subjects will be
documented separately.

Capabilities not stated in the assignment brief or in the finalized decisions
are not requirements. In particular, this specification does not require
authentication, authorization, roles, pagination, sorting, ticket deletion,
notifications, attachments, audit history, or comment editing or deletion.

## 3. Terminology

- **Ticket:** A support request managed by the system.
- **Comment:** Text added to a ticket.
- **Supported ticket field:** Title, description, priority, or assignee.
- **Status transition:** A request to change a ticket from its current status
  to another status.
- **Terminal status:** A status from which no further status transition is
  allowed.
- **Persisted state:** Ticket data stored in the configured database.

## 4. Functional Requirements

- **FR-001 — Create ticket:** The system shall allow a user to create a support
  ticket through the UI.
- **FR-002 — List tickets:** The system shall allow a user to view a list of
  tickets through the UI.
- **FR-003 — View ticket details:** The system shall allow a user to open and
  view the details of a ticket through the UI.
- **FR-004 — Update title:** The system shall allow a user to update a ticket's
  title.
- **FR-005 — Update description:** The system shall allow a user to update a
  ticket's description.
- **FR-006 — Update priority:** The system shall allow a user to update a
  ticket's priority.
- **FR-007 — Update assignee:** The system shall allow a user to update a
  ticket's assignee.
- **FR-008 — Add comment:** The system shall allow a user to add a comment to a
  ticket.
- **FR-009 — Search tickets:** The system shall allow a user to search tickets
  by keyword.
- **FR-010 — Filter tickets:** The system shall allow a user to filter tickets
  by status.
- **FR-011 — Combine search and filtering:** The system shall allow keyword
  search and status filtering to be applied together.
- **FR-012 — Change status:** The system shall allow a user to request a ticket
  status change through the UI.
- **FR-013 — REST access:** Backend capabilities required by this specification
  shall be exposed through a REST API.

## 5. Business Rules

### 5.1 Ticket values

- **BR-001 — Ticket identifier:** Each ticket shall have a unique identifier.
- **BR-002 — Initial status:** A newly created ticket shall have status `OPEN`.
- **BR-003 — Priority values:** A ticket priority shall be one of `LOW`,
  `MEDIUM`, `HIGH`, or `CRITICAL`.
- **BR-004 — Assignee representation:** An assignee shall be represented as a
  String and shall be optional.
- **BR-005 — Comment information:** A comment shall contain a required body and
  a timestamp.

### 5.2 Search and filtering

- **BR-006 — Search matching:** Keyword search shall perform a
  case-insensitive partial match against ticket title and description.
- **BR-007 — Status filtering:** Status filtering shall use an exact status
  match.
- **BR-008 — Combined criteria:** When keyword search and status filtering are
  supplied together, returned tickets shall satisfy both criteria.

### 5.3 Ticket status lifecycle

The only allowed ticket status transitions are:

- **BR-009:** `OPEN` to `IN_PROGRESS`
- **BR-010:** `IN_PROGRESS` to `RESOLVED`
- **BR-011:** `RESOLVED` to `CLOSED`
- **BR-012:** `OPEN` to `CANCELLED`
- **BR-013:** `IN_PROGRESS` to `CANCELLED`

The following rules apply to all status-change requests:

- **BR-014 — Unlisted transitions:** Any status transition not listed in
  BR-009 through BR-013 is invalid and shall be rejected by the backend.
- **BR-015 — Same-status request:** A request to transition a ticket to its
  current status is invalid and shall be rejected by the backend.
- **BR-016 — Terminal statuses:** `CLOSED` and `CANCELLED` are terminal
  statuses.
- **BR-017 — Atomic rejection:** Rejection of a status transition shall not
  modify the persisted ticket state.

Examples of invalid transitions include, but are not limited to:

- `CLOSED` to `OPEN`
- `RESOLVED` to `OPEN`
- `CANCELLED` to `OPEN`
- `OPEN` to `RESOLVED`
- `OPEN` to `CLOSED`
- `IN_PROGRESS` to `CLOSED`

### 5.4 Modification rules

- **BR-018 — Terminal ticket fields:** The title, description, priority, and
  assignee of a ticket in `CLOSED` or `CANCELLED` status shall not be updated.
- **BR-019 — Terminal ticket comments:** Comments shall not be added to a
  ticket in `CLOSED` or `CANCELLED` status.
- **BR-020 — Ordinary updates:** Ordinary ticket updates shall be limited to
  title, description, priority, and assignee and shall not change ticket
  status.
- **BR-021 — Dedicated status operation:** Status changes shall be performed
  through an operation dedicated to status transitions.

## 6. Validation Rules

- **VR-001 — Backend enforcement:** The backend shall validate input and shall
  reject input that violates this specification.
- **VR-002 — Required title:** Ticket title shall be required.
- **VR-003 — Title length:** Ticket title shall contain no more than 200
  characters.
- **VR-004 — Required description:** Ticket description shall be required.
- **VR-005 — Description length:** Ticket description shall contain no more
  than 5,000 characters.
- **VR-006 — Priority validation:** A supplied priority shall satisfy BR-003.
- **VR-007 — Comment body:** A comment body shall be required.
- **VR-008 — Status validation:** A status-change request shall satisfy
  BR-009 through BR-017.
- **VR-009 — Terminal modification validation:** A field update or comment
  request for a terminal ticket shall be rejected in accordance with BR-018 or
  BR-019.

No other field limits, formats, or validation rules are established by this
specification.

## 7. Error-Handling Requirements

- **ER-001 — Missing ticket:** A request for a ticket that does not exist shall
  produce HTTP status `404 Not Found`.
- **ER-002 — Validation error:** A request rejected for input validation shall
  produce HTTP status `400 Bad Request`.
- **ER-003 — Invalid transition:** A request rejected because the requested
  status transition is invalid shall produce HTTP status `409 Conflict`.
- **ER-004 — Structured error:** Backend errors covered by this specification
  shall use a structured JSON response containing `code`, `message`, `status`,
  and `path`.
- **ER-005 — Meaningful UI error:** The UI shall display a meaningful error
  when an operation fails. The displayed error shall communicate the failure
  to the user rather than failing silently.
- **ER-006 — Backend authority:** UI validation or controls shall not replace
  backend enforcement of validation and lifecycle rules.

This document does not assign HTTP statuses to error categories other than
those in ER-001 through ER-003.

## 8. Persistence Requirements

- **PR-001 — Database persistence:** Ticket data shall be persisted in a
  database.
- **PR-002 — Application database:** The application shall use PostgreSQL as
  its application database.
- **PR-003 — Test database:** Automated tests shall use H2 as their database.
- **PR-004 — Restart durability:** Persisted ticket data shall remain available
  after the application is restarted while using the same database.
- **PR-005 — Rejected-transition durability:** A rejected status transition
  shall not change any persisted ticket state, as required by BR-017.

## 9. Technical Constraints

- **TC-001:** The backend shall use Java 21.
- **TC-002:** The backend shall use Spring Boot.
- **TC-003:** Persistence shall use PostgreSQL and H2 as specified by PR-002
  and PR-003.
- **TC-004:** Backend functionality shall be exposed through a REST API.
- **TC-005:** The frontend shall use React, Next.js, or an equivalent frontend
  technology.
- **TC-006:** Cursor shall be used during project development.
- **TC-007:** GitHub Copilot shall be used during project development.
- **TC-008:** Secrets shall not be committed to the repository.
- **TC-009:** Ordinary updates shall use HTTP `PATCH` and shall be limited to
  title, description, priority, and assignee.
- **TC-010:** Status changes shall use a dedicated status-transition endpoint.
- **TC-011:** State-machine integration tests shall call the REST API and
  verify database state.

TC-009 and TC-010 establish operation-level constraints only. Detailed paths,
payloads, responses, and the remainder of the API contract are deferred to the
API specification.

## 10. Acceptance Criteria

- **AC-001 — Create through UI:** A user can create a ticket through the UI,
  and the created ticket has status `OPEN`.
- **AC-002 — View list:** A user can view a list of tickets through the UI.
- **AC-003 — Open details:** A user can open and view ticket details through
  the UI.
- **AC-004 — Update supported fields:** A user can update title, description,
  priority, and assignee for a non-terminal ticket.
- **AC-005 — Change assignee:** A user can assign or clear the optional String
  assignee for a non-terminal ticket.
- **AC-006 — Add comments:** A user can add a comment with a required body to a
  non-terminal ticket, and the comment has a timestamp.
- **AC-007 — Search:** A user can search tickets using a case-insensitive,
  partial keyword match across title and description.
- **AC-008 — Filter:** A user can filter tickets by exact status.
- **AC-009 — Combine search and filter:** A user can apply keyword search and
  exact status filtering together.
- **AC-010 — Change status through UI:** A user can request an allowed status
  transition through the UI.
- **AC-011 — Allowed transitions succeed:** Each transition in BR-009 through
  BR-013 succeeds when requested for a ticket in the corresponding source
  status.
- **AC-012 — Invalid transitions fail:** Every transition not listed in BR-009
  through BR-013, including a same-status request, is rejected by the backend
  with `409 Conflict`.
- **AC-013 — Rejection preserves state:** After a status transition is
  rejected, the persisted ticket state is unchanged.
- **AC-014 — Terminal restrictions:** Ticket fields cannot be updated and
  comments cannot be added when a ticket is `CLOSED` or `CANCELLED`.
- **AC-015 — Restart durability:** Ticket data persisted in PostgreSQL remains
  available after an application restart using the same database.
- **AC-016 — Backend validation:** The backend rejects input that violates
  VR-002 through VR-009; input-validation failures produce `400 Bad Request`.
- **AC-017 — Missing ticket:** An operation targeting a nonexistent ticket
  produces `404 Not Found`.
- **AC-018 — Structured errors:** Errors governed by ER-004 contain `code`,
  `message`, `status`, and `path` in a JSON response.
- **AC-019 — UI error display:** The UI displays a meaningful error when a
  backend operation fails.
- **AC-020 — Valid-transition integration coverage:** Integration tests call
  the REST API for every transition in BR-009 through BR-013 and verify the
  resulting database state.
- **AC-021 — Invalid-transition integration coverage:** Integration tests call
  the REST API for invalid transitions, verify their rejection, and verify
  that the database state is unchanged.
- **AC-022 — No committed secrets:** The repository contains no committed
  secrets.

## 11. Traceability Summary

### 11.1 Assignment capabilities

| Original assignment requirement | Requirement IDs | Acceptance criteria |
| --- | --- | --- |
| 1. Create a support ticket | FR-001, BR-002, VR-002–VR-006 | AC-001, AC-016 |
| 2. List tickets | FR-002 | AC-002 |
| 3. View ticket details | FR-003 | AC-003 |
| 4. Update ticket title | FR-004, BR-018, BR-020, VR-002–VR-003, TC-009 | AC-004, AC-014, AC-016 |
| 5. Update ticket description | FR-005, BR-018, BR-020, VR-004–VR-005, TC-009 | AC-004, AC-014, AC-016 |
| 6. Update ticket priority | FR-006, BR-003, BR-018, BR-020, VR-006, TC-009 | AC-004, AC-014, AC-016 |
| 7. Update ticket assignee | FR-007, BR-004, BR-018, BR-020, TC-009 | AC-004, AC-005, AC-014 |
| 8. Add comments | FR-008, BR-005, BR-019, VR-007, VR-009 | AC-006, AC-014, AC-016 |
| 9. Search by keyword | FR-009, BR-006 | AC-007 |
| 10. Filter by status | FR-010, BR-007 | AC-008 |
| 11. Persist ticket data | PR-001–PR-005 | AC-013, AC-015 |
| 12. Perform backend input validation | VR-001–VR-009, ER-002, ER-006 | AC-016 |
| 13. Display meaningful UI errors | ER-005 | AC-019 |

### 11.2 Assignment lifecycle rules

| Original lifecycle requirement | Requirement IDs | Acceptance criteria |
| --- | --- | --- |
| `OPEN` to `IN_PROGRESS` | BR-009 | AC-011, AC-020 |
| `IN_PROGRESS` to `RESOLVED` | BR-010 | AC-011, AC-020 |
| `RESOLVED` to `CLOSED` | BR-011 | AC-011, AC-020 |
| `OPEN` to `CANCELLED` | BR-012 | AC-011, AC-020 |
| `IN_PROGRESS` to `CANCELLED` | BR-013 | AC-011, AC-020 |
| Reject all other transitions | BR-014–BR-016, VR-008, ER-003 | AC-012, AC-021 |
| Rejection must preserve persisted state | BR-017, PR-005 | AC-013, AC-021 |

### 11.3 Assignment acceptance and technical constraints

| Original assignment item | Requirement IDs / acceptance criteria |
| --- | --- |
| Create, list, open, update, assign, comment, search, and filter through the UI | FR-001–FR-010; AC-001–AC-009 |
| Valid transitions succeed | BR-009–BR-013; AC-011, AC-020 |
| Invalid transitions are rejected | BR-014–BR-017; AC-012–AC-013, AC-021 |
| Data survives application restart | PR-004; AC-015 |
| Backend rejects invalid input | VR-001–VR-009; AC-016 |
| UI displays meaningful errors | ER-005; AC-019 |
| Valid and invalid transition integration tests | TC-011; AC-020–AC-021 |
| No secrets committed | TC-008; AC-022 |
| Java 21 | TC-001 |
| Spring Boot | TC-002 |
| PostgreSQL for application persistence and H2 for automated tests | TC-003, PR-002–PR-003 |
| REST API | FR-013, TC-004 |
| React/Next.js or equivalent | TC-005 |
| Cursor | TC-006 |
| GitHub Copilot | TC-007 |
