# Support Ticket Management System State Machine Specification

## 1. Purpose and Authority

This document defines the complete lifecycle state machine for a Ticket. It
formalizes the approved states, transitions, rejection behavior, terminal
behavior, and lifecycle invariants without prescribing implementation code.

Authority, from highest to lowest, is:

1. `spec/requirements.md` defines the approved lifecycle and business rules.
2. `spec/architecture.md` assigns authoritative lifecycle enforcement to the
   backend service/business layer.
3. `spec/data-model.md` defines the persisted status values and keeps
   transition authorization out of the database schema.
4. `spec/api-contract.md` defines the dedicated status operation and HTTP
   behavior.

This specification introduces no additional status, transition, lifecycle
rule, or product capability.

## 2. States

The Ticket lifecycle contains exactly five states:

| State | Approved lifecycle meaning |
| --- | --- |
| `OPEN` | Initial Ticket state. It may transition to `IN_PROGRESS` or `CANCELLED`. |
| `IN_PROGRESS` | Active lifecycle state. It may transition to `RESOLVED` or `CANCELLED`. |
| `RESOLVED` | Resolved lifecycle state. It may transition only to `CLOSED`. |
| `CLOSED` | Terminal state with no allowed outgoing transition. |
| `CANCELLED` | Terminal state with no allowed outgoing transition. |

These definitions do not imply ownership, SLA, timing, notification, audit, or
reopening behavior.

## 3. Initial State

Ticket creation has exactly one lifecycle outcome:

`CREATE → OPEN`

Every newly created Ticket starts in `OPEN`. A client cannot supply or choose
the initial status. The backend assigns `OPEN` when creating the Ticket.

## 4. Complete Transition Matrix

The matrix below covers all 25 combinations of the five current states and
five requested target states.

| Current state \ Target state | `OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED` | `CANCELLED` |
| --- | --- | --- | --- | --- | --- |
| `OPEN` | REJECTED | **ALLOWED** | REJECTED | REJECTED | **ALLOWED** |
| `IN_PROGRESS` | REJECTED | REJECTED | **ALLOWED** | REJECTED | **ALLOWED** |
| `RESOLVED` | REJECTED | REJECTED | REJECTED | **ALLOWED** | REJECTED |
| `CLOSED` | REJECTED | REJECTED | REJECTED | REJECTED | REJECTED |
| `CANCELLED` | REJECTED | REJECTED | REJECTED | REJECTED | REJECTED |

The only allowed cells are:

| Current | Target | Result |
| --- | --- | --- |
| `OPEN` | `IN_PROGRESS` | ALLOWED |
| `OPEN` | `CANCELLED` | ALLOWED |
| `IN_PROGRESS` | `RESOLVED` | ALLOWED |
| `IN_PROGRESS` | `CANCELLED` | ALLOWED |
| `RESOLVED` | `CLOSED` | ALLOWED |

Every other matrix cell is rejected. The matrix is authoritative for all
current-state and target-state combinations.

## 5. Transition Rules

- **SM-001 — Initial state:** Every new Ticket starts in `OPEN`.
- **SM-002 — OPEN transitions:** `OPEN` may transition only to `IN_PROGRESS`
  or `CANCELLED`.
- **SM-003 — IN_PROGRESS transitions:** `IN_PROGRESS` may transition only to
  `RESOLVED` or `CANCELLED`.
- **SM-004 — RESOLVED transition:** `RESOLVED` may transition only to
  `CLOSED`.
- **SM-005 — CLOSED terminal behavior:** `CLOSED` has no outgoing lifecycle
  transition.
- **SM-006 — CANCELLED terminal behavior:** `CANCELLED` has no outgoing
  lifecycle transition.
- **SM-007 — Same-state rejection:** A request whose target equals the current
  state is rejected.
- **SM-008 — Closed transition set:** Any transition not explicitly listed as
  allowed by SM-002 through SM-004 is rejected.
- **SM-009 — Rejection invariant:** A rejected transition must not modify the
  Ticket's persisted status or other persisted Ticket state.
- **SM-010 — Dedicated operation:** Status changes are performed only through
  the dedicated status operation defined by the API contract.

## 6. Transition Evaluation

The implementation-independent evaluation process is:

1. Resolve the requested Ticket and read its current persisted status.
2. Validate that the requested target status is one of `OPEN`, `IN_PROGRESS`,
   `RESOLVED`, `CLOSED`, or `CANCELLED`.
3. Evaluate the pair `(currentStatus, targetStatus)` against the complete
   matrix in Section 4.
4. If the pair is allowed, change and persist the Ticket status to the target
   status.
5. If the pair is rejected, return a lifecycle conflict and leave persisted
   Ticket state unchanged.

The client supplies only the requested target status. It does not supply the
authoritative current state, and it cannot authorize a transition. Evaluation
always uses current persisted backend state.

This process does not prescribe a framework, class, repository, transaction
mechanism, or database implementation.

## 7. Terminal States

`CLOSED` and `CANCELLED` are the only terminal states.

For a Ticket in either terminal state:

- No status transition is allowed.
- Ordinary updates to title, description, priority, or assignee are rejected.
- New Comments are rejected.

Terminal status means no outgoing lifecycle transition. The field-update and
Comment restrictions are separate approved business rules that also apply to
these two states.

This specification defines no deletion or reopening behavior.

## 8. Separation from Ordinary Ticket Updates

- `PATCH /api/tickets/{id}` may update only title, description, priority, and
  assignee.
- Ordinary Ticket PATCH cannot accept or change status.
- Status changes occur only through
  `PATCH /api/tickets/{id}/status`.
- Frontend controls may hide or disable unavailable actions for usability, but
  those controls are advisory.
- Backend service-layer validation is authoritative for every lifecycle
  decision.

This separation prevents ordinary field updates from bypassing transition
validation.

## 9. Invalid Transition Examples

Representative rejected transitions include:

- `OPEN → OPEN`
- `OPEN → RESOLVED`
- `OPEN → CLOSED`
- `IN_PROGRESS → OPEN`
- `IN_PROGRESS → IN_PROGRESS`
- `IN_PROGRESS → CLOSED`
- `RESOLVED → OPEN`
- `RESOLVED → IN_PROGRESS`
- `RESOLVED → RESOLVED`
- `RESOLVED → CANCELLED`
- `CLOSED → OPEN`
- `CLOSED → IN_PROGRESS`
- `CLOSED → RESOLVED`
- `CLOSED → CLOSED`
- `CLOSED → CANCELLED`
- `CANCELLED → OPEN`
- `CANCELLED → IN_PROGRESS`
- `CANCELLED → RESOLVED`
- `CANCELLED → CLOSED`
- `CANCELLED → CANCELLED`

These examples are illustrative. The complete transition matrix in Section 4
is authoritative and rejects every unlisted pair.

## 10. API Error Mapping

The dedicated status operation uses the approved API error behavior:

| Situation | HTTP status | Error code | Lifecycle effect |
| --- | --- | --- | --- |
| Target value is missing, null, outside the five-state enum, or request input is malformed | `400 Bad Request` | `VALIDATION_ERROR` | No lifecycle change |
| Ticket does not exist | `404 Not Found` | `TICKET_NOT_FOUND` | No Ticket exists to change |
| Target is an approved state but the current/target pair is rejected | `409 Conflict` | `INVALID_STATUS_TRANSITION` | Persisted Ticket state remains unchanged |

An invalid lifecycle transition is never ignored, converted to success, or
treated as a no-op.

No additional lifecycle error code is introduced by this specification.

## 11. Persistence Invariant

For every rejected lifecycle transition:

`persistedStatusAfterRequest = persistedStatusBeforeRequest`

The rejected request must not modify any other persisted Ticket state.

For every successful lifecycle transition, the approved target state becomes
the Ticket's persisted current status.

This specification states observable persistence outcomes but does not define
transaction, locking, repository, or database implementation details.

## 12. Testable Invariants

- **INV-001:** Every newly created Ticket has status `OPEN`.
- **INV-002:** Exactly five current/target state pairs are allowed.
- **INV-003:** Every state pair not listed as allowed is rejected.
- **INV-004:** Every same-state transition is rejected.
- **INV-005:** `CLOSED` has no outgoing transition.
- **INV-006:** `CANCELLED` has no outgoing transition.
- **INV-007:** A rejected transition leaves persisted Ticket state unchanged.
- **INV-008:** Ordinary `PATCH /api/tickets/{id}` cannot change status.
- **INV-009:** A Ticket in `CLOSED` or `CANCELLED` rejects ordinary field
  updates.
- **INV-010:** A Ticket in `CLOSED` or `CANCELLED` rejects new Comments.
- **INV-011:** Backend service-layer lifecycle enforcement is authoritative;
  frontend state or controls cannot authorize a transition.
- **INV-012:** A successful allowed transition persists its target status.
- **INV-013:** A valid target state in a rejected pair produces
  `409 Conflict` with `INVALID_STATUS_TRANSITION`.
- **INV-014:** An invalid target value produces `400 Bad Request`.
- **INV-015:** A missing Ticket produces `404 Not Found`.

These invariants define observable behavior for later test design. They do not
constitute test implementation.

## 13. Requirement Traceability

| State-machine rule or concern | Requirements | Architecture | Data model | API contract |
| --- | --- | --- | --- | --- |
| SM-001 Initial `OPEN` | BR-002, AC-001 | Sections 5 and 6; AD-002 | Ticket status; DM-004 | Sections 5 and 9 |
| SM-002 `OPEN` transitions | BR-009, BR-012 | Section 5; AD-002 | DM-004, DM-010 | Section 9.2 |
| SM-003 `IN_PROGRESS` transitions | BR-010, BR-013 | Section 5; AD-002 | DM-004, DM-010 | Section 9.2 |
| SM-004 `RESOLVED → CLOSED` | BR-011 | Section 5; AD-002 | DM-004, DM-010 | Section 9.2 |
| SM-005 CLOSED terminal | BR-014, BR-016 | Sections 5 and 6.2; AD-002 | DM-004, DM-010 | Sections 9.2 and 14 |
| SM-006 CANCELLED terminal | BR-014, BR-016 | Sections 5 and 6.2; AD-002 | DM-004, DM-010 | Sections 9.2 and 14 |
| SM-007 Same-state rejection | BR-015, VR-008 | Sections 5 and 6.3; AD-002 | DM-010 | Sections 9.2 and 11.3 |
| SM-008 Reject every unlisted pair | BR-014, VR-008, ER-003 | Sections 5 and 6.3; AD-002 | DM-010 | Sections 9.2, 9.4, and 12.3 |
| SM-009 Preserve persisted state | BR-017, PR-005, AC-013, AC-021 | Sections 5 and 8.2; AD-007 | DM-010 | Sections 9.4 and 11 |
| SM-010 Dedicated status operation | BR-020, BR-021, TC-009, TC-010 | Section 9; AD-003 | Section 7 | Sections 8, 9, and 14 |
| Terminal field restrictions | BR-018, VR-009, AC-014 | Sections 5, 6.2, and 9; AD-002 | Section 7; DM-010 | Sections 8.3, 11.2, and API-007 |
| Terminal Comment restriction | BR-019, VR-009, AC-014 | Sections 5, 6.2, and 9; AD-002 | DM-003, DM-007, DM-010 | Sections 10.3, 11.2, and API-007 |
| Invalid transition conflict | ER-003, AC-012 | Sections 3.6 and 7; AD-008 | DM-010 | Sections 9.4 and 12.3 |
| Valid target enum | BR-014, VR-008 | Section 6.3 | DM-004 | Sections 9.4 and 11.1 |
| REST/database integration verification | TC-011, AC-020, AC-021 | Section 10 | Current persisted status | Section 17 |

Only IDs and sections present in the approved specifications are referenced.

## 14. Explicit Non-Goals

This state machine does not introduce:

- Reopening Tickets.
- Additional statuses.
- Additional transitions.
- Automatic transitions.
- Scheduled transitions.
- Notifications.
- SLA behavior.
- Audit history.
- Transition history.
- Authentication, authorization, or roles.
- Ticket or Comment deletion.
- Lifecycle timestamps.
- Any lifecycle capability outside the approved requirements.

## 15. Resolved Questions

The Ticket lifecycle is fully specified:

- The state set is closed at five states.
- The allowed transition set is closed at five transitions.
- Every other current/target pair is rejected.
- Initial and terminal behavior is defined.
- API rejection behavior and persistence outcomes are defined.

No lifecycle question remains unresolved for implementation. Any future request
to add or reopen a state, add or remove a transition, or alter terminal
behavior requires an approved specification change before implementation.

## 16. Consistency Review

This specification was checked to confirm:

1. Exactly five states are defined.
2. Exactly five transitions are marked ALLOWED.
3. `OPEN → IN_PROGRESS` is allowed.
4. `IN_PROGRESS → RESOLVED` is allowed.
5. `RESOLVED → CLOSED` is allowed.
6. `OPEN → CANCELLED` is allowed.
7. `IN_PROGRESS → CANCELLED` is allowed.
8. No transition from `CLOSED` is allowed.
9. No transition from `CANCELLED` is allowed.
10. Every same-state transition is rejected.
11. `RESOLVED → CANCELLED` is rejected.
12. No reopening transition exists.
13. Ordinary Ticket PATCH cannot modify status.
14. Terminal field-update and Comment restrictions remain intact.
15. Invalid lifecycle transitions map to `409 Conflict` and
    `INVALID_STATUS_TRANSITION`.
16. Invalid target values map to `400 Bad Request`.
17. A missing Ticket maps to `404 Not Found`.
18. Rejected transitions leave persisted state unchanged.
19. No additional lifecycle requirement or capability was introduced.
