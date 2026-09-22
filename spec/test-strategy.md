# Test Strategy Specification

## 1. Purpose

This document defines the testing approach for the Support Ticket Management
System before implementation and test generation begin. It explains what must
be verified, why the behavior is important, which test level owns the primary
verification, and how tests trace to approved requirements and acceptance
criteria.

Authority, from highest to lowest, is:

1. `spec/requirements.md`
2. `spec/architecture.md`
3. `spec/data-model.md`
4. `spec/api-contract.md`
5. `spec/state-machine.md`
6. `spec/ui-flow.md`
7. This test strategy

Project engineering and testing rules guide test quality and separation of
concerns but do not override these specifications.

This is a strategy specification. It does not define test classes, test method
names, framework configuration, fixtures, SQL, or implementation code.

## 2. Testing Principles

1. **Verify approved behavior:** Tests verify observable requirements and
   contracts rather than private implementation structure.
2. **Keep the backend authoritative:** UI tests verify presentation and
   interaction, while backend tests prove validation, business rules, and
   lifecycle enforcement.
3. **Test rules at their owning layer:** Service/business tests focus on
   business decisions; API tests focus on HTTP contracts; repository tests
   focus on persistence queries and integrity.
4. **Integrate critical behavior:** Important workflows must also be exercised
   across REST, service, repository, and database boundaries.
5. **Test success and rejection:** Every important business rule needs
   positive and negative coverage plus boundary coverage where applicable.
6. **Verify rejected-state integrity:** Where rejection must preserve state,
   tests verify both the error and the persisted state after the request.
7. **Cover the complete lifecycle matrix:** All 25 current/target status pairs
   receive REST/database integration coverage, not only representative cases.
8. **Remain deterministic:** Tests explicitly control inputs, persisted data,
   time where relevant, and expected outcomes.
9. **Remain isolated:** Tests do not depend on pre-existing state, execution
   order, or data left by another test.
10. **Avoid unnecessary duplication:** A behavior has one primary owning test
    level; supporting levels repeat only what is necessary to prove boundaries
    or critical end-to-end behavior.
11. **Do not test invented behavior:** Tests must not impose requirements for
    blank-string rejection, extra length limits, sorting, pagination,
    authentication, or other absent capabilities.
12. **Communicate intent:** Test names and assertions should make the approved
    behavior and requirement reference evident.

## 3. Test Levels

### 3.1 Unit Tests

Unit tests exercise service/business decisions and focused domain behavior in
isolation from HTTP and a real database.

Primary responsibilities:

- State-transition decision logic for all allowed and rejected pairs.
- Same-state transition rejection.
- `CLOSED` and `CANCELLED` terminal behavior.
- Service decisions that reject ordinary field updates for terminal Tickets.
- Service decisions that reject Comments for terminal Tickets.
- Preservation of the in-memory operation result when validation rejects a
  mutation.
- Coordination of keyword OR semantics and keyword/status AND semantics where
  that logic exists in the service layer.
- Business-rule distinctions between valid request values and current-state
  conflicts.

Unit tests should use focused substitutes only for collaborators needed to
isolate the decision. They should not attempt to prove:

- HTTP method, path, JSON, or status-code behavior.
- Real database constraints, queries, transactions, or durability.
- PostgreSQL/H2 compatibility.
- Controller serialization or centralized error mapping.
- Frontend rendering or interaction.

The complete transition decision table may be covered at unit level for fast
feedback, but that does not replace the mandatory REST/database matrix tests.

### 3.2 Controller/API Tests

Controller/API tests verify the public REST boundary and centralized error
translation without claiming full persistence behavior.

Required coverage:

- Exact HTTP methods and paths.
- Request JSON deserialization and response serialization.
- Required fields and maximum lengths.
- Approved Priority and TicketStatus values.
- Decimal-string identifier representations.
- RFC 3339 UTC Comment timestamp representation.
- Malformed identifier behavior.
- Missing-resource mapping.
- Unknown request-field rejection.
- Empty ordinary PATCH rejection.
- PATCH omission and assignee-null semantics.
- Rejection of `status`, `id`, Comments, and timestamps in ordinary PATCH.
- Dedicated status endpoint accepting only `targetStatus`.
- Comment endpoint accepting only `body`.
- Query parameters `keyword` and `status`.
- `200` and `201` success mappings.
- `400`, `404`, and `409` error mappings.
- Structured errors containing `code`, `message`, `status`, and `path`.
- Absence of persistence entities and internal details in API responses.

These tests may isolate controllers and exception translation from the
database. They prove contract mapping, not that data was committed.

### 3.3 Repository/Data Access Tests

Repository tests verify relational persistence behavior using the automated
test database.

Required coverage:

- Persist and retrieve a Ticket.
- Persist and retrieve Comments associated with a Ticket.
- One-to-many Ticket/Comment relationship and required ownership.
- Required persistence mappings and relational integrity relevant to the
  approved data model, where those constraints are actually implemented at
  the persistence boundary.
- Current status storage and retrieval.
- Updates to title, description, priority, and assignee.
- Clearing a nullable assignee.
- Case-insensitive partial title match.
- Case-insensitive partial description match.
- Title-or-description keyword semantics.
- Exact filtering for every approved status.
- Combined keyword AND exact-status querying.
- Empty-result behavior.

Repository tests must not assume that every API validation rule is implemented
as a database constraint. Backend validation remains the authoritative
enforcement mechanism unless a persistence constraint is explicitly required
by the approved specifications.

Repository tests must not decide whether a state transition is allowed or
whether a terminal Ticket may be modified. Those are service/business rules.
The database constrains values and relationships but does not encode the
transition graph.

### 3.4 Integration Tests

Integration tests exercise multiple backend layers together:

`REST API → Controller → Service → Repository → H2`

Required workflow coverage:

- Create and retrieve Ticket.
- List Ticket summaries.
- Partially update each approved field.
- Clear assignee.
- Add and retrieve Comments through Ticket details.
- Search, status filter, and combined query.
- Propagate request validation into the approved structured response.
- Propagate missing-resource and business conflicts.
- Persist accepted ordinary updates and Comments.
- Ensure rejected terminal operations do not mutate stored data.

Integration tests verify observable backend behavior and database state, not
private method calls.

### 3.5 State-Machine Integration Tests

State-machine integration testing is mandatory and receives complete 5×5
matrix coverage.

For each of the 25 current/target pairs, a test must:

1. Create or establish an independently controlled Ticket in the required
   current state.
2. Call `PATCH /api/tickets/{id}/status` with only `targetStatus`.
3. Verify the HTTP response and error contract.
4. Read the persisted Ticket state from the test database.
5. For an allowed pair, verify the target status was persisted.
6. For a rejected pair, verify `409 Conflict`,
   `INVALID_STATUS_TRANSITION`, and an unchanged persisted Ticket.
7. Verify no unrelated Ticket field was changed by a rejected transition.

The five allowed pairs are:

- `OPEN → IN_PROGRESS`
- `OPEN → CANCELLED`
- `IN_PROGRESS → RESOLVED`
- `IN_PROGRESS → CANCELLED`
- `RESOLVED → CLOSED`

The other 20 pairs are rejected, including every same-state pair, every pair
from `CLOSED`, every pair from `CANCELLED`, and
`RESOLVED → CANCELLED`.

These tests call the REST API and verify database state. Unit-only transition
tests or API tests without persistence assertions cannot satisfy TC-011,
AC-020, or AC-021.

Additional status-operation integration cases cover:

- Invalid target value: `400 VALIDATION_ERROR`.
- Missing/null target: `400 VALIDATION_ERROR`.
- Malformed Ticket identifier: `400 VALIDATION_ERROR`.
- Well-formed missing Ticket identifier: `404 TICKET_NOT_FOUND`.

### 3.6 Frontend/UI Tests

Frontend tests verify approved rendering, interaction, API request formation,
and error handling. They do not prove backend authorization.

Required UI coverage:

- Ticket List summaries and navigation.
- Create form success and validation failure.
- Ticket Detail fields and Comments.
- Empty list, no-results, and no-Comments states.
- Ordinary edit request containing only approved changed fields.
- Assignee clearing with JSON `null`.
- Five approved status action mappings.
- No status/edit/Comment action for terminal Tickets.
- Comment submission success and error.
- Keyword, status, and combined query formation.
- List/detail/loading states.
- Mutation progress and duplicate-submission prevention.
- Meaningful handling of `400 VALIDATION_ERROR`.
- Meaningful handling of `404 TICKET_NOT_FOUND`.
- Meaningful handling of `409 INVALID_STATUS_TRANSITION`.
- Meaningful handling of `409 TERMINAL_TICKET_CONFLICT`.
- Ticket refresh after lifecycle or terminal conflict.
- Generic network/unexpected-error presentation.

UI tests need representative lifecycle cases only because backend
state-machine integration tests own all 25 lifecycle combinations. UI tests
must still prove that controls are advisory and backend rejection is handled.

## 4. Functional Test Coverage

| Area | Required Coverage | Primary Test Level | Supporting Test Level |
| --- | --- | --- | --- |
| Ticket creation | Required fields, optional assignee, generated ID, initial `OPEN`, `201` | Integration | Controller/API, UI |
| Ticket listing | Summary fields only, array response, empty list | Integration | Controller/API, UI |
| Ticket details | Approved fields, embedded Comments, empty Comments | Integration | Controller/API, UI |
| Title update | Partial PATCH, 200-character boundary, persistence | Integration | Controller/API, unit |
| Description update | Partial PATCH, 5,000-character boundary, persistence | Integration | Controller/API, unit |
| Priority update | Four approved values and invalid value rejection | Integration | Controller/API |
| Assignee update | String assignment and persistence | Integration | Controller/API, UI |
| Assignee clearing | Explicit JSON `null`; omitted field unchanged | Integration | Controller/API, UI |
| Comments | Required body, generated ID/timestamp, relationship, terminal rejection | Integration | Repository, Controller/API, UI |
| Keyword search | Case-insensitive partial title OR description | Repository | Integration, UI |
| Status filtering | Exact match for all five values | Repository | Integration, UI |
| Combined query | Keyword match AND exact status match | Repository | Integration, UI |
| Valid transitions | All five accepted through REST and persisted | State-machine integration | Unit |
| Invalid transitions | All 20 rejected with 409 and unchanged data | State-machine integration | Unit, Controller/API |
| Same-state transitions | Five rejected pairs | State-machine integration | Unit |
| Terminal states | No outgoing transition from `CLOSED`/`CANCELLED` | State-machine integration | Unit, UI |
| Terminal field updates | Each approved field rejected for both terminal states; unchanged data | Integration | Unit, UI |
| Terminal Comments | Comment rejected for both terminal states; no row added | Integration | Unit, UI |
| Request validation | Approved required, enum, and maximum constraints only | Controller/API | Integration, UI |
| Missing Ticket | 404 and `TICKET_NOT_FOUND` for Ticket-scoped operations | Controller/API | Integration, UI |
| Malformed ID | 400 and `VALIDATION_ERROR` | Controller/API | Integration |
| Unknown request fields | 400 for create, PATCH, status, and Comment requests | Controller/API | Integration |
| Structured errors | Required `code`, `message`, `status`, and `path`; no internal details | Controller/API | Integration, UI |
| Persistence | Successful Tickets, updates, status, and Comments stored | Integration | Repository |
| Restart durability | Data available after application process restart using same PostgreSQL database | PostgreSQL-backed verification | Integration evidence |
| UI error handling | Approved errors and generic failures displayed meaningfully | UI | API contract tests |
| No secrets | Repository content and configuration practices contain no committed secrets | Static repository check/review | CI/review process |

## 5. State Machine Test Strategy

### 5.1 Matrix coverage

The authoritative state set is:

- `OPEN`
- `IN_PROGRESS`
- `RESOLVED`
- `CLOSED`
- `CANCELLED`

The test suite maintains an explicit 5×5 case set:

| Current \ Target | `OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED` | `CANCELLED` |
| --- | --- | --- | --- | --- | --- |
| `OPEN` | REJECTED | ALLOWED | REJECTED | REJECTED | ALLOWED |
| `IN_PROGRESS` | REJECTED | REJECTED | ALLOWED | REJECTED | ALLOWED |
| `RESOLVED` | REJECTED | REJECTED | REJECTED | ALLOWED | REJECTED |
| `CLOSED` | REJECTED | REJECTED | REJECTED | REJECTED | REJECTED |
| `CANCELLED` | REJECTED | REJECTED | REJECTED | REJECTED | REJECTED |

Coverage is complete only when all 25 cells execute as REST/database
integration cases:

- 5 allowed.
- 20 rejected.
- 5 same-state rejected.
- 5 outgoing attempts from `CLOSED` rejected.
- 5 outgoing attempts from `CANCELLED` rejected.

The categories overlap; they describe required assertions rather than
additional matrix cells.

### 5.2 Allowed-transition assertions

For each allowed pair, verify:

- Request uses `PATCH /api/tickets/{id}/status`.
- Payload contains only the approved `targetStatus`.
- Response is `200 OK`.
- Response status equals the target.
- Persisted status equals the target.
- Other Ticket fields remain unchanged.

### 5.3 Rejected-transition assertions

For each rejected pair, verify:

- Response is `409 Conflict`.
- Error `code` is `INVALID_STATUS_TRANSITION`.
- Error contains `code`, `message`, `status`, and `path`.
- Persisted status after the request equals persisted status before it.
- Title, description, priority, and assignee remain unchanged.
- No success response or silent no-op is reported.

### 5.4 Status-operation input and resource cases

Separately from the 25 valid-enum state pairs:

- Missing `targetStatus`: 400.
- Null `targetStatus`: 400.
- Value outside the five approved statuses: 400.
- Unknown request field: 400.
- Malformed Ticket identifier: 400.
- Well-formed nonexistent Ticket identifier: 404.

These cases verify request/resource handling and do not count as transition
matrix cells.

### 5.5 Initial state

Ticket creation integration tests verify that:

- The client does not submit status.
- The backend returns `OPEN`.
- The persisted new Ticket status is `OPEN`.

## 6. Validation and Error Test Strategy

Only approved contract validation is tested:

| Case | Expected result |
| --- | --- |
| Missing/null create title | `400 VALIDATION_ERROR` |
| Title at 200 characters | Accepted when other input is valid |
| Title over 200 characters | `400 VALIDATION_ERROR` |
| Missing/null create description | `400 VALIDATION_ERROR` |
| Description at 5,000 characters | Accepted when other input is valid |
| Description over 5,000 characters | `400 VALIDATION_ERROR` |
| Missing/null create priority | `400 VALIDATION_ERROR` |
| Priority outside `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | `400 VALIDATION_ERROR` |
| Missing/null Comment body | `400 VALIDATION_ERROR` |
| Missing/null/invalid target status | `400 VALIDATION_ERROR` |
| Empty ordinary PATCH object | `400 VALIDATION_ERROR` |
| Unknown create field | `400 VALIDATION_ERROR` |
| Unknown PATCH field, including `status` | `400 VALIDATION_ERROR` |
| Unknown status-operation field | `400 VALIDATION_ERROR` |
| Unknown Comment field | `400 VALIDATION_ERROR` |
| Invalid `status` query value | `400 VALIDATION_ERROR` |
| Otherwise syntactically invalid query/request | `400 VALIDATION_ERROR` |
| Malformed Ticket identifier | `400 VALIDATION_ERROR` |
| Well-formed nonexistent Ticket | `404 TICKET_NOT_FOUND` |
| Valid target in rejected state pair | `409 INVALID_STATUS_TRANSITION` |
| Field update on terminal Ticket | `409 TERMINAL_TICKET_CONFLICT` |
| Comment on terminal Ticket | `409 TERMINAL_TICKET_CONFLICT` |

Every structured error test verifies:

- HTTP status.
- `code`.
- Human-readable `message` presence.
- Numeric `status` matching the HTTP status.
- Request `path`.
- Absence of stack trace and internal persistence detail.

Tests do not add or expect:

- Minimum String lengths.
- Whitespace-only rejection.
- Automatic trimming.
- Assignee format or maximum length.
- Comment-body maximum length.
- Additional error codes.

## 7. Search and Filter Test Strategy

### 7.1 Keyword search

Use deterministic Ticket titles and descriptions to verify:

- Lowercase keyword matches differently cased title text.
- Uppercase keyword matches differently cased description text.
- A substring matches within title.
- A substring matches within description.
- Title match succeeds when description does not match.
- Description match succeeds when title does not match.
- A Ticket is not duplicated when both fields match.
- A nonmatching keyword returns `200 OK` with `[]`.
- Empty `keyword` follows the API contract and acts as no restrictive keyword
  criterion.

Tests must not expect fuzzy matching, stemming, ranking, tokenization, or
advanced syntax.

### 7.2 Status filtering

Verify exact filtering independently for:

- `OPEN`
- `IN_PROGRESS`
- `RESOLVED`
- `CLOSED`
- `CANCELLED`

For each status, returned summaries all have that exact status. A valid status
with no matching Ticket returns `200 OK` and `[]`. An invalid status value
returns `400 VALIDATION_ERROR`.

### 7.3 Combined search and status filter

Use data that independently distinguishes both predicates:

| Keyword matches | Status matches | Expected inclusion |
| --- | --- | --- |
| Yes | Yes | Included |
| Yes | No | Excluded |
| No | Yes | Excluded |
| No | No | Excluded |

Verify the request uses both `keyword` and `status` and that the backend applies
keyword **AND** status semantics. Empty combined results are successful, not
errors.

### 7.4 API and UI responsibilities

Repository and backend integration tests prove matching semantics. UI tests
verify correct query construction, retained controls, and empty-result
presentation; they do not reimplement or independently prove database search.

## 8. Terminal-State Test Strategy

Run the following rejected-operation set for both `CLOSED` and `CANCELLED`:

| Operation | Expected response | Persistence assertion |
| --- | --- | --- |
| Any of the five approved target statuses | `409 INVALID_STATUS_TRANSITION` | Status and all Ticket data unchanged |
| Update title | `409 TERMINAL_TICKET_CONFLICT` | Title and all Ticket data unchanged |
| Update description | `409 TERMINAL_TICKET_CONFLICT` | Description and all Ticket data unchanged |
| Update priority | `409 TERMINAL_TICKET_CONFLICT` | Priority and all Ticket data unchanged |
| Update assignee | `409 TERMINAL_TICKET_CONFLICT` | Assignee and all Ticket data unchanged |
| Clear assignee | `409 TERMINAL_TICKET_CONFLICT` | Assignee and all Ticket data unchanged |
| Add Comment | `409 TERMINAL_TICKET_CONFLICT` | No Comment added; Ticket data unchanged |

Tests verify current database state before and after each rejected operation.
UI tests verify that terminal Tickets do not offer edit, status, or Comment
actions, while backend integration tests prove enforcement.

No deletion or reopening test is included because those operations do not
exist.

## 9. Persistence Test Strategy

### 9.1 Transaction-level persistence tests

Focused repository or service-persistence tests verify:

- Ticket and Comment writes are committed when valid.
- Reads return stored values and required relationships.
- Accepted ordinary updates modify only supplied approved fields.
- Accepted status transitions modify only current status.
- Rejected transitions commit no mutation.
- Rejected terminal updates and Comments commit no mutation.

Where a rejection invariant applies, compare complete relevant persisted state
before and after, not only the response.

### 9.2 Integration persistence tests

REST/database integration tests verify:

- Created Ticket is retrievable and listed.
- Updated fields remain changed on a subsequent GET.
- Cleared assignee remains null on a subsequent GET.
- Created Comment is returned in Ticket details.
- Search/filter query results derive from persisted records.
- Allowed transition status is present on a subsequent GET and in database
  state.
- Rejected transition status and other fields are unchanged.
- Rejected terminal Comment creates no Comment row.

### 9.3 PostgreSQL restart/durability verification

PR-004 and AC-015 require a PostgreSQL-backed verification using the same
database across an application process restart:

1. Start the application connected to a controlled PostgreSQL test database.
2. Create known Ticket data through the approved API.
3. Confirm it is persisted.
4. Stop and restart the application without recreating or clearing that
   database.
5. Retrieve the Ticket through the API.
6. Verify the stored Ticket data remains available and unchanged.

This is environment-level verification. An H2 in-memory lifecycle, a mocked
repository, or restarting only a test context cannot by itself prove
PostgreSQL restart durability.

## 10. H2 vs PostgreSQL Test Strategy

### 10.1 H2 role

H2 is the approved automated-test database. It supports fast, repeatable:

- Repository tests.
- Backend integration tests.
- State-machine REST/database integration tests.
- Relational nullability, relationship, and common query verification.

### 10.2 What H2 does not prove

H2 does not prove:

- PostgreSQL process-independent restart durability.
- PostgreSQL SQL dialect behavior.
- PostgreSQL-specific generated-key or timestamp behavior.
- PostgreSQL-specific constraints, types, collations, or indexes.
- Production query plans or performance.
- Case behavior that differs between database engines.

### 10.3 PostgreSQL-backed verification

Non-automated PostgreSQL-backed acceptance verification is required when
behavior depends on PostgreSQL, especially:

- Restart durability.
- Any PostgreSQL-specific SQL or mapping introduced during implementation.
- Search case/substring semantics if H2 and PostgreSQL differ.
- Database-specific schema constraints or generated identifiers.

The model should remain portable where approved. Tests must not introduce a
PostgreSQL-specific feature merely to create more test coverage.
Automated database tests continue to use H2 as required by PR-003.

## 11. Test Data Strategy

- Each test creates or explicitly controls the Ticket and Comment data it
  needs.
- Tests begin from known database state and do not rely on developer data.
- Each case uses known source status and target status.
- Priority data covers all four approved values where value handling matters.
- Search tests use deliberately distinct titles and descriptions so title OR
  description behavior is unambiguous.
- Combined-query data separates keyword matching from status matching.
- Comments are created through approved mechanisms unless the test is
  specifically focused on repository persistence.
- Identifiers are treated as opaque values returned by setup, not assumed
  sequence numbers.
- Timestamp assertions use contract format or controlled ranges rather than
  assuming an exact wall-clock value unless time is explicitly controlled.
- Comment ordering is not asserted because no ordering guarantee exists.
- Test data contains no author, User, Ticket timestamp, deletion metadata, or
  unsupported field.
- Cleanup or reset behavior is selected with the test framework so each test
  remains isolated.

No implementation-specific fixture library or seed format is prescribed.

## 12. Test Isolation and Repeatability

Tests must be:

- Deterministic under repeated execution.
- Independently runnable.
- Independent of execution order.
- Isolated from unrelated test data and mutation.
- Explicit about initial persisted state.
- Free from shared mutable clocks, identifiers, or caches unless controlled.

Permitted isolation approaches may include transaction rollback, schema reset,
database recreation, or uniquely scoped test data, depending on the eventual
test framework. The chosen approach must not hide commit/rollback behavior in
tests intended to prove persistence.

Tests should not rely on manually inserted records. A repository test may
create persisted setup directly because persistence is its subject; API and
workflow tests should establish data through approved boundaries where
practical.

Parallel execution must not cause cases to observe or modify one another's
Tickets.

## 13. API Contract Verification

Contract tests verify:

- `POST /api/tickets`
- `GET /api/tickets`
- `GET /api/tickets/{id}`
- `PATCH /api/tickets/{id}`
- `PATCH /api/tickets/{id}/status`
- `POST /api/tickets/{id}/comments`
- Optional `keyword` and `status` query parameters.
- `201 Created` for Ticket and Comment creation.
- `200 OK` for retrieval and accepted updates/transitions.
- Ticket summary, Ticket resource, Ticket detail, Comment, and error shapes.
- Decimal-string Ticket and Comment IDs.
- RFC 3339 UTC Comment timestamp with `Z`.
- No Ticket timestamp or Comment author.
- Assignee omitted/null creation behavior.
- Assignee omission on PATCH leaves it unchanged.
- Assignee null on PATCH clears it.
- Comments embedded in Ticket details with no ordering assertion.
- Unknown request-field rejection.
- Empty PATCH rejection.
- Correct `code`, `message`, `status`, and `path`.

Ordinary PATCH tests explicitly submit `status` and verify `400
VALIDATION_ERROR` with no status change. Dedicated status tests verify that the
request accepts only `targetStatus` and does not trust client-supplied current
state or transition matrices.

Responses must not expose persistence annotations, entity-only fields,
repository types, stack traces, or internal exception names.

## 14. UI Test Strategy

| UI area | Required behavior |
| --- | --- |
| Create | Required fields, approved Priority choices, optional assignee, loading, duplicate prevention, success navigation, validation error |
| List | Required summary fields only, loading, empty list, detail/create navigation |
| Search/filter | Correct query parameters, retained criteria, no-results state, OR/AND semantics delegated to backend |
| Detail | Approved Ticket fields, Comments, empty Comments, no Ticket timestamp or author |
| Edit | Approved changed fields only, no empty request, assignee clearing, success refresh, 400/404/409 handling |
| Status | Exactly five action mappings, dedicated payload, success refresh, invalid-transition error and stale-state refresh |
| Comment | Body only, loading, duplicate prevention, success refresh, validation/terminal error |
| Terminal Ticket | No edit, status, or Comment action for `CLOSED` and `CANCELLED` |
| Errors | Meaningful 400, 404, both approved 409 codes, and generic network/server failures |
| Accessibility baseline | Labels, understandable actions/loading, visible operation errors, textual status communication |

UI tests use controlled API responses or an integration environment according
to the behavior under test:

- Focused UI tests verify rendering and requests efficiently.
- UI/API integration tests verify representative successful and rejected
  workflows.
- Backend integration tests remain authoritative for validation, persistence,
  and all 25 state pairs.

The UI suite does not duplicate every matrix pair. It verifies action mapping,
terminal control behavior, and at least a representative stale
`409 INVALID_STATUS_TRANSITION` refresh flow.

## 15. Traceability

| Requirement / acceptance criterion | Behavior to verify | Test level | Strategy reference |
| --- | --- | --- | --- |
| FR-001, BR-002–BR-004, AC-001 | Create Ticket with generated ID and `OPEN`; required creation data | Integration, UI | 3.4, 4, 6, 14 |
| FR-002, AC-002 | List summaries and empty collection | Integration, UI | 3.4, 4, 14 |
| FR-003, AC-003 | Retrieve and render Ticket detail | Integration, UI | 3.4, 4, 14 |
| FR-004, VR-002–VR-003, AC-004 | Update valid title through API/UI; boundary and rejection | Controller/API, integration, UI | 4, 6, 13, 14 |
| FR-005, VR-004–VR-005, AC-004 | Update valid description through API/UI; boundary and rejection | Controller/API, integration, UI | 4, 6, 13, 14 |
| FR-006, BR-003, VR-006, AC-004 | Update approved Priority through API/UI; reject invalid value | Controller/API, integration, UI | 4, 6, 14 |
| FR-007, BR-004, AC-004–AC-005 | Assign, omit, and clear assignee through API/UI | Integration, UI | 4, 13, 14 |
| FR-008, BR-005, VR-007, AC-006 | Add and retrieve Comment body/timestamp | Integration, UI | 3.4, 4, 6, 14 |
| FR-009, BR-006, AC-007 | Case-insensitive partial title OR description search | Repository, integration, UI | 7 |
| FR-010, BR-007, AC-008 | Exact filtering for each status | Repository, integration, UI | 7 |
| FR-011, BR-008, AC-009 | Combined keyword AND status behavior | Repository, integration, UI | 7 |
| FR-012, BR-009–BR-013, AC-010–AC-011 | Five allowed transitions through REST and persistence | State-machine integration, UI | 3.5, 5, 14 |
| BR-014–BR-017, VR-008, ER-003, PR-005, AC-012–AC-013 | Twenty rejected pairs, same-state rejection, 409, unchanged persistence | State-machine integration | 3.5, 5 |
| BR-018, VR-009, AC-014 | Terminal field updates rejected without mutation | Unit, integration, UI | 8 |
| BR-019, VR-009, AC-014 | Terminal Comments rejected without insertion | Unit, integration, UI | 8 |
| BR-020, TC-009 | Ordinary PATCH fields only; status rejected | Controller/API, integration | 6, 13 |
| BR-021, TC-010 | Dedicated status operation only | Controller/API, state-machine integration | 5, 13 |
| VR-001–VR-007, ER-002, ER-006, AC-016 | Request validation remains authoritative | Controller/API, integration | 6, 13 |
| VR-008–VR-009, AC-016 | Lifecycle and terminal-state validation remains authoritative | Unit/service, integration | 5, 8 |
| ER-001, AC-017 | Missing Ticket returns 404 code/structure | Controller/API, integration, UI | 6, 13, 14 |
| ER-004, AC-018 | Structured errors contain required `code`, `message`, `status`, and `path` fields | Controller/API, integration | 6, 13 |
| ER-005, AC-019 | UI displays meaningful operation errors | UI | 14 |
| PR-001–PR-003 | Relational persistence in PostgreSQL/H2 roles | Repository, integration | 9, 10 |
| PR-004, AC-015 | Data survives application restart with same PostgreSQL database | PostgreSQL-backed acceptance verification | 9.3, 10.3 |
| TC-011, AC-020 | Valid transition REST/database integration coverage | State-machine integration | 3.5, 5 |
| TC-011, AC-021 | Invalid transition REST/database coverage and unchanged state | State-machine integration | 3.5, 5 |
| TC-008, AC-022 | No committed secrets | Static repository check and review | 4, 16 |
| FR-013, TC-004 | Required backend capabilities exposed through approved REST contract | Controller/API, integration | 3.2, 13 |
| TC-005, UI-INV-001–UI-INV-021 | Approved UI behaviors and frontend boundary | UI | 3.6, 14 |

This table covers AC-001 through AC-022. Supporting BR, VR, ER, PR, and TC
references identify the governing rule behind each acceptance criterion.

## 16. Coverage Expectations

- Every approved functional requirement has test coverage.
- Every acceptance criterion AC-001 through AC-022 has corresponding
  verification.
- All five allowed state transitions have focused and REST/database
  integration coverage.
- All 20 rejected state pairs have REST/database integration coverage.
- Every same-state pair is tested.
- Every API error category and code is tested.
- Every approved validation constraint has negative coverage and applicable
  boundary coverage.
- Every terminal restriction is tested for both `CLOSED` and `CANCELLED`.
- Search OR semantics and combined-query AND semantics are tested with data
  that would detect an incorrect implementation.
- Critical business rules have focused tests plus appropriate integration
  coverage.
- Persistence assertions accompany all lifecycle cases for which persisted
  state is part of the requirement.
- UI coverage verifies observable approved flows without attempting to replace
  backend enforcement.

No arbitrary code-coverage percentage is imposed. Coverage adequacy is judged
against requirements, decision branches, boundary conditions, and
traceability.

## 17. Out-of-Scope Testing

The strategy excludes tests for:

- Authentication.
- Authorization.
- Users or roles.
- Notifications.
- Attachments.
- Audit or transition history.
- Pagination.
- Sorting.
- Ticket deletion.
- Comment editing or deletion.
- Reopening Tickets.
- Additional lifecycle states or transitions.
- Automatic or scheduled transitions.
- SLA behavior.
- Fuzzy, ranked, advanced, or external search.
- Ticket timestamps or Comment authors.

Tests must not create implied requirements for these capabilities.

## 18. Risks and Testing Gaps

| Risk | Consequence | Mitigation |
| --- | --- | --- |
| Tests assert private implementation structure | Refactoring breaks tests without behavior change | Assert contracts, decisions, and persisted outcomes |
| Happy-path-only coverage | Rejections and boundaries fail in production | Require positive, negative, and boundary cases |
| Incomplete 5×5 matrix | Invalid transition may be accepted | Maintain explicit 25-case REST/database set |
| UI-only lifecycle testing | Requests can bypass UI controls | Keep service and integration tests authoritative |
| Assert 409 without database check | Rejected transition may still mutate data | Compare persisted state before and after every rejected pair |
| Assume H2 proves PostgreSQL | Dialect or durability defect remains hidden | Add PostgreSQL-backed verification where behavior depends on PostgreSQL |
| Weak search data | OR/AND errors go undetected | Use records that isolate each predicate combination |
| Status accepted in ordinary PATCH | Lifecycle enforcement can be bypassed | Contract and integration test unknown/prohibited `status` field |
| Shared database state | Order-dependent or flaky outcomes | Reset, rollback, recreate, or uniquely scope data per test |
| Unknown fields silently accepted | Contract allows unintended mutation input | Test unknown fields on every write shape |
| UI remains stale after conflict | User sees invalid controls or status | UI test 409 message and detail refresh |
| Mock-heavy integration claims | Persistence and mapping remain unproven | Reserve “integration” for real application/database boundaries |
| Comment order asserted accidentally | Tests impose unsupported sorting | Treat Comment order as unspecified |
| Restart test uses recreated database | Durability appears proven when data was reseeded | Reuse the exact PostgreSQL database across process restart |
| Error test checks only status | Wrong code or unsafe payload may pass | Assert structure, code, status, path, and no internal detail |

No automated H2 suite alone can close the PostgreSQL durability gap. The
PostgreSQL restart verification in Section 9.3 remains required evidence.

## 19. Test Execution Summary

The intended feedback sequence is:

1. Unit tests.
2. Controller/API tests.
3. Repository tests.
4. General backend integration tests.
5. Complete state-machine integration tests.
6. Frontend/UI tests.
7. PostgreSQL-backed verification where required.

Faster focused tests run earlier; broader environment-dependent verification
runs after lower-level failures are resolved. This ordering does not create
test dependencies: every test remains independently runnable within its
required environment.

Actual test generation begins only after this strategy is approved.

## 20. Resolved Testing Decisions

### TS-D-001 — Complete matrix at integration level

- **Decision:** Execute all 25 state pairs through REST and verify persisted
  database state.
- **Basis:** TC-011, AC-020, AC-021, and the approved testing rules.

### TS-D-002 — Behavior ownership by test level

- **Decision:** Assign one primary level to each behavior and add supporting
  coverage only for important boundaries and workflows.
- **Basis:** Layered architecture and maintainable, nonduplicative testing.

### TS-D-003 — H2 automated tests plus PostgreSQL evidence

- **Decision:** Use H2 for approved automated database tests and require
  PostgreSQL-backed verification for restart durability and
  PostgreSQL-specific behavior.
- **Basis:** PR-002–PR-004, TC-003, and data-model portability constraints.

### TS-D-004 — Full-state comparison after rejection

- **Decision:** Verify relevant persisted Ticket data, not only status, after
  rejected lifecycle and terminal operations.
- **Basis:** BR-017, PR-005, API invariants, and risk of partial mutation.

### TS-D-005 — Representative lifecycle UI coverage

- **Decision:** UI tests cover action mapping, terminal controls, successful
  transitions, and stale-state conflicts without repeating all 25 backend
  matrix cases.
- **Basis:** Backend authority and UI-flow testable invariants.

### TS-D-006 — No Comment ordering assertion

- **Decision:** Tests render and validate Comments without asserting order.
- **Basis:** API-003 and the approved data-model deferral.

### TS-D-007 — Acceptance-based coverage

- **Decision:** Measure completeness by requirement, acceptance-criterion,
  matrix, boundary, and risk coverage rather than an arbitrary code percentage.
- **Basis:** Project testing rules and the absence of an approved percentage.

## 21. Unresolved Questions

No product or behavioral testing question remains unresolved.

Implementation planning must still select test frameworks, database lifecycle
mechanisms, environment provisioning, and CI execution details. Those are
implementation choices, not changes to this strategy.

The PostgreSQL-backed restart verification requires an environment that can
preserve one controlled database across an application process restart. Its
provisioning mechanism must be decided before that non-automated acceptance
verification is executed, but the required behavior and evidence are already
specified.

## 22. Consistency Review

The strategy was reviewed against every approved specification:

- **Requirements:** All FR-001–FR-013 and AC-001–AC-022 have verification;
  BR, VR, ER, PR, and TC rules are traced at their appropriate levels.
- **Architecture:** Controllers are tested for HTTP concerns, services for
  business rules, repositories for persistence, and integrations across
  boundaries.
- **Data model:** Tests cover Ticket, Comment, approved enums, relationship,
  nullability, search access, and portability without adding entities or
  fields.
- **API contract:** Every endpoint, approved status, representation, error
  code, query semantic, and unknown-field rule has a verification owner.
- **State machine:** All five states and all 25 pairs receive mandatory
  REST/database coverage; five succeed and 20 are rejected.
- **UI flow:** All three views, detail action flows, loading/empty states,
  errors, terminal controls, and stale-state refresh behavior are covered.

Self-review findings:

- **Contradictions:** None identified.
- **Missing acceptance-criterion coverage:** None identified.
- **Wrong-level responsibility:** None identified after separating controller,
  service, repository, integration, and UI responsibilities.
- **Newly invented requirements:** None. Explicit negative assertions prevent
  tests from creating new validation or product behavior.
- **Unresolved testing decisions:** Framework, environment, and CI mechanics
  remain implementation choices; PostgreSQL restart environment provisioning
  must be selected before execution.
- **False-completeness risk:** H2 cannot prove PostgreSQL restart durability,
  API-only 409 checks cannot prove unchanged persistence, and representative
  transition examples cannot prove the full matrix. Sections 5, 9, 10, and 18
  preserve the additional evidence required for each gap.
