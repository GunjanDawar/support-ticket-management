# Implementation Plan and Task Breakdown

## 1. Purpose

This document converts the frozen Support Ticket Management System
specifications into a dependency-aware, incrementally verifiable
implementation plan.

Implementation must follow the approved specifications. Implementation
choices, framework defaults, generated code, or developer preferences must not
silently alter requirements, API behavior, lifecycle rules, persistence
semantics, UI behavior, or test obligations.

This is a planning artifact. It does not create source code, tests, SQL,
migrations, configuration, or dependencies.

## 2. Implementation Principles

1. Implement behavior from the frozen specifications in authority order.
2. Keep backend validation and lifecycle enforcement authoritative.
3. Preserve the Controller → Service → Repository → Database layering.
4. Keep API request/response DTOs separate from persistence entities.
5. Keep controllers focused on HTTP binding, validation, delegation, and
   response mapping.
6. Keep business rules, lifecycle decisions, and terminal restrictions in the
   service/business layer.
7. Keep persistence and query behavior in repositories/data access.
8. Keep ordinary Ticket PATCH separate from status transition handling.
9. Apply request validation at the DTO/API boundary and business/state
   validation in services.
10. Translate failures through centralized structured error handling.
11. Use transaction boundaries around state-changing service operations where
    required to preserve approved outcomes.
12. Add only dependencies needed to implement the approved stack and behavior.
13. Implement only approved entities, fields, endpoints, states, transitions,
    views, and validation.
14. Create tests according to `spec/test-strategy.md`, including complete
    state-matrix integration coverage.
15. Finish each major phase with objective verification before dependent work
    proceeds.
16. Never commit secrets; use external configuration for environment-specific
    values.

**No task may introduce a new product requirement, API endpoint, state,
transition, validation rule, or persistence field without first updating the
approved specification through the SDD review process.**

**If an implementation choice can change observable API behavior, persistence
behavior, validation behavior, lifecycle behavior, or UI behavior, the
implementation agent must not decide silently. It must stop, identify the
affected specification, and request an SDD decision before proceeding.**

## 3. Proposed Implementation Phases

### Phase 1 — Project Foundation

#### Scope

- Establish the Java 21 Spring Boot backend build.
- Add only the backend capabilities required for REST, request validation,
  relational persistence, PostgreSQL application access, and H2 automated
  tests.
- Establish the selected React, Next.js, or equivalent frontend project.
- Create the repository structure consistent with backend layers and the three
  approved frontend views.
- Define configuration profiles or equivalent separation for:
  - PostgreSQL application configuration.
  - H2 automated-test configuration.
  - Local development values.
- Load secrets and environment-specific credentials externally.
- Establish build, test, lint, and local start commands without adding product
  behavior.
- Pin dependency versions and update applicable lockfiles when dependencies are
  selected.
- Use Cursor and GitHub Copilot during the implementation workflow as required,
  and record actual usage accurately in project documentation.

The phase does not select optional infrastructure, external search, messaging,
authentication, or unrelated libraries.

#### Verification checkpoint

- Backend builds under Java 21 and starts with a controlled local
  configuration.
- Frontend installs/builds and starts.
- H2 test configuration can initialize.
- PostgreSQL configuration can be supplied externally.
- No credentials or secrets are committed.
- Empty baseline test commands execute successfully.

### Phase 2 — Persistence Model

#### Scope

- Implement Ticket and Comment as the only persistent entities.
- Implement generated 64-bit integer persistence identifiers.
- Implement textual status values:
  `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`.
- Implement textual Priority values:
  `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Implement Ticket fields:
  title, description, priority, nullable assignee, and current status.
- Implement Comment fields:
  body, required timestamp, and required Ticket association.
- Implement `Ticket 1 → 0..* Comment`.
- Implement required nullability, approved field capacities, relational
  integrity, and portable enum representation.
- Implement the approved Ticket status index and Comment Ticket-foreign-key
  index where appropriate.
- Preserve PostgreSQL/H2-compatible mappings unless an approved implementation
  decision explicitly requires otherwise.

Do not add User, author, Ticket timestamp, update timestamp, lifecycle
timestamp, audit/history, deletion metadata, category, attachment, or SLA
fields.

#### Verification checkpoint

- Persistence context initializes under H2.
- Ticket and Comment can be persisted and retrieved.
- Comment ownership and one-to-many retrieval work.
- Nullable assignee can be stored and cleared.
- Enum values round-trip using stable textual meaning.
- Persistence mappings contain no unsupported field/entity.

### Phase 3 — API DTOs and Error Model

#### Scope

Implement separate API representations for:

- Create Ticket request.
- Partial Ticket update request.
- Status target request.
- Add Comment request.
- Ticket summary response.
- Ticket resource response.
- Ticket detail response.
- Comment response.
- Structured error response.

Contract details:

- JSON identifiers are decimal strings even though persistence IDs are 64-bit
  integers.
- Comment timestamps use RFC 3339 UTC representation with `Z`.
- Creation allows omitted or null assignee.
- PATCH omission leaves assignee unchanged; explicit null clears it.
- Ordinary PATCH DTO has no status field.
- Status DTO contains only `targetStatus`.
- Comment request contains only `body`.
- Unknown request fields are rejected.
- Persistence entities are never serialized directly.

Plan centralized error translation for:

- `400 VALIDATION_ERROR`
- `404 TICKET_NOT_FOUND`
- `409 INVALID_STATUS_TRANSITION`
- `409 TERMINAL_TICKET_CONFLICT`

All structured errors contain `code`, `message`, `status`, and `path`, without
stack traces or persistence details.

The frozen API contract does not define unexpected-server-failure status/code
behavior, while the project API standards require that behavior to be defined.
Do not invent it during implementation. Resolve this specification gap through
SDD review before implementing unexpected-failure translation.

#### Verification checkpoint

- DTO serialization/deserialization matches API examples and field sets.
- Decimal-string IDs and Comment timestamps serialize correctly.
- Unknown fields and invalid DTO input map to 400.
- Error response fields and codes match the contract.
- No persistence-only field leaks into API responses.

### Phase 4 — Repository/Data Access Layer

#### Scope

- Implement Ticket persistence and identifier lookup.
- Implement Comment persistence and Ticket-scoped retrieval.
- Retrieve Ticket detail with associated Comments without adding an ordering
  guarantee.
- Implement Ticket collection queries for:
  - No criteria.
  - Case-insensitive partial keyword match on title OR description.
  - Exact status match.
  - Keyword AND exact status together.
- Treat empty keyword as no restrictive keyword criterion.
- Return the complete result set without pagination or sorting.

Repositories provide persistence/query operations only. They do not authorize
transitions or terminal-state mutations.

Do not introduce fuzzy search, relevance ranking, external search, Redis,
Elasticsearch, pagination, or sorting.

#### Verification checkpoint

- Repository tests persist and retrieve Ticket/Comment relationships.
- Keyword search proves case-insensitive partial title OR description
  semantics.
- Each status filter is exact.
- Combined criteria use AND.
- Empty results are successful.
- No repository contains lifecycle decisions.

### Phase 5 — Core Business Services

#### Scope

Implement service operations for:

- Create Ticket with backend-assigned `OPEN`.
- List/search/filter Tickets.
- Retrieve Ticket details.
- Partially update title, description, priority, and assignee.
- Add Comment with backend-assigned identity, ownership, and timestamp.
- Request a dedicated status transition.

Business rules owned here:

- Initial status is always `OPEN`.
- Current persisted status is authoritative.
- Only the five approved transitions succeed.
- Same-state and every unlisted transition are rejected.
- Rejected transitions leave persisted Ticket state unchanged.
- `CLOSED` and `CANCELLED` reject field updates and Comments.
- Ordinary update operations cannot change status.
- Comment addition validates the current Ticket state.
- Missing Tickets produce the approved not-found result.
- State-changing use cases use service-level transaction boundaries appropriate
  to atomic approved outcomes.

Frontend control visibility is not authorization. Service enforcement applies
to every caller.

#### Verification checkpoint

- Service unit tests cover all transition decisions.
- Terminal update and Comment restrictions have positive/negative coverage.
- Creation always results in `OPEN`.
- Rejected operations do not invoke or commit unintended mutations.
- Search/filter service coordination preserves repository semantics.

### Phase 6 — REST Controllers and API Completion

#### Scope

- Implement exactly the six approved endpoint operations.
- Bind and validate the approved DTO for each operation.
- Delegate to the corresponding service operation.
- Map successful results and failures to approved HTTP behavior.
- Ensure ordinary PATCH cannot bind or forward status.
- Ensure the status endpoint accepts only target status.
- Keep controllers free of lifecycle and persistence logic.

#### Verification checkpoint

- Controller/API tests verify every method/path, payload, response shape, and
  status.
- Unknown fields, malformed identifiers, missing resources, and errors map
  correctly.
- All responses preserve DTO/entity separation.
- No additional endpoint is exposed.

### Phase 7 — Backend Integration and Contract Verification

#### Scope

- Integrate REST, services, repositories, and H2.
- Implement approved create/read/update/Comment/search/filter integration
  coverage.
- Implement complete REST/database lifecycle matrix coverage.
- Verify database state after accepted and rejected operations.
- Verify terminal-operation conflicts and unchanged data.

#### Verification checkpoint

- Every backend endpoint passes contract tests.
- All five allowed transitions pass.
- All 20 rejected pairs return 409 and preserve persisted state.
- Search/filter integration behavior matches BR-006 through BR-008.
- Structured errors match the API contract.

### Phase 8 — Frontend Views and API Integration

#### Scope

- Implement Ticket List, Create Ticket, and Ticket Detail as the three primary
  views.
- Implement edit, transition, and Comment action flows in Ticket detail.
- Use only the six approved endpoint operations.
- Implement loading, empty, no-result, submission, and error states.
- Implement terminal-state control behavior as advisory usability.
- Refresh Ticket detail after successful mutations and relevant 409 conflicts.
- Implement the approved usability baseline: visible labels, clear action
  names, keyboard-operable available controls, visible operation errors,
  understandable loading/disabled states, and textual status communication.

#### Verification checkpoint

- UI tests cover UI-INV-001 through UI-INV-021.
- All approved flows work against the backend.
- Exactly five lifecycle actions are exposed.
- Terminal Tickets expose no mutation action.
- Backend conflicts remain visible and trigger refresh.
- No unsupported UI capability appears.

### Phase 9 — Full Verification, Review, and Acceptance

#### Scope

- Run focused and integrated automated suites.
- Perform PostgreSQL-backed restart/durability acceptance verification using
  the same controlled database across application restart.
- Review implementation against all frozen specifications and project rules.
- Classify review findings as confirmed defects or optional suggestions.
- Fix confirmed specification deviations and rerun affected verification.

#### Verification checkpoint

- All automated tests pass.
- Complete lifecycle matrix evidence is available.
- PostgreSQL restart verification passes.
- No secrets are committed.
- Code and specification-consistency reviews have no unresolved critical or
  major defect.

## 4. State-Machine Implementation Plan

### 4.1 Frozen lifecycle

The state set is exactly:

- `OPEN`
- `IN_PROGRESS`
- `RESOLVED`
- `CLOSED`
- `CANCELLED`

The allowed transition set is exactly:

- `OPEN → IN_PROGRESS`
- `OPEN → CANCELLED`
- `IN_PROGRESS → RESOLVED`
- `IN_PROGRESS → CANCELLED`
- `RESOLVED → CLOSED`

The other 20 current/target combinations are rejected.

### 4.2 Behavior to implement

The status-transition service operation must:

1. Resolve the Ticket and read current persisted status.
2. Validate that target status is an approved value at the API boundary.
3. Compare current/target against the frozen allowed set.
4. Return the approved invalid-transition conflict for an unlisted pair.
5. Persist target status for an allowed pair.
6. Leave status and all other persisted Ticket state unchanged for a rejected
   pair.

The client never supplies authoritative current status. The database stores
current status but does not encode the transition graph. The plan does not
prescribe an enum map, strategy pattern, class name, or other internal
implementation pattern.

### 4.3 Verification

- Focused service tests cover decision behavior.
- REST/database integration tests cover all 25 pairs.
- Same-state requests are included among the 20 rejected pairs.
- Every target from `CLOSED` and `CANCELLED` is rejected.
- `RESOLVED → CANCELLED` is rejected.
- Invalid target input is 400; missing Ticket is 404; valid target in an
  invalid pair is 409.

## 5. API Controller Implementation Plan

Controllers implement only the operations below.

| Endpoint | Purpose | Request DTO | Response DTO | Success | Validation/service/error responsibility | References |
| --- | --- | --- | --- | --- | --- | --- |
| `POST /api/tickets` | Create Ticket | Create Ticket request | Ticket resource | `201 Created` | Validate title/description/Priority; service assigns ID and `OPEN`; 400 on request failure | FR-001, BR-002–BR-004, VR-002–VR-006; API Section 5 |
| `GET /api/tickets` | List/search/filter | Optional `keyword`, `status` query | Ticket summary array | `200 OK` | Validate status query; service/repository apply approved semantics; 400 invalid query | FR-002, FR-009–FR-011, BR-006–BR-008; API Section 6 |
| `GET /api/tickets/{id}` | Ticket detail with Comments | Path ID | Ticket detail | `200 OK` | Validate identifier; service retrieves Ticket; 400 malformed, 404 missing | FR-003, FR-008, ER-001; API Section 7 |
| `PATCH /api/tickets/{id}` | Partial ordinary update | Update Ticket request | Ticket resource | `200 OK` | Validate nonempty approved field set; service enforces terminal rule; 400/404/409 | FR-004–FR-007, BR-018, BR-020, VR-009, TC-009; API Section 8 |
| `PATCH /api/tickets/{id}/status` | Dedicated status transition | Target status request | Ticket resource | `200 OK` | Validate target; service evaluates persisted state; 400/404/409 | FR-012, BR-009–BR-017, BR-021, VR-008, TC-010; API Section 9 |
| `POST /api/tickets/{id}/comments` | Add Comment | Add Comment request | Comment response | `201 Created` | Validate body; service owns timestamp/relationship and terminal rule; 400/404/409 | FR-008, BR-005, BR-019, VR-007, VR-009; API Section 10 |

Controller responsibilities:

- Parse HTTP input.
- Trigger DTO validation.
- Delegate once to the applicable service operation.
- Map approved DTOs and statuses.

Controllers must not access repositories directly, expose entities, choose
lifecycle outcomes, or contain terminal rules.

**Do not add additional endpoints.**

## 6. Search and Filtering Implementation Plan

### 6.1 Keyword

- Accept optional `keyword`.
- Apply case-insensitive partial matching.
- Match title OR description.
- Ensure a Ticket matching both fields appears once.
- Treat omitted or empty keyword as no restrictive keyword criterion.

### 6.2 Status

- Accept optional status.
- Restrict supplied values to the five approved statuses.
- Apply exact matching.
- Omit the status criterion when absent.

### 6.3 Combined

- Apply keyword AND status when both are supplied.
- Return `200 OK` and an empty array when no Ticket matches.

### 6.4 Verification

- Repository tests verify keyword-only behavior, status-only behavior,
  keyword AND status behavior, and empty/no-match behavior.
- Integration tests verify query binding and response summaries.
- UI tests verify query construction and empty-result presentation.
- Non-automated PostgreSQL-aware verification is required if actual search
  semantics differ from H2.

No fuzzy matching, ranking, advanced syntax, pagination, sorting, or external
search is implemented.

## 7. Frontend Implementation Plan

### 7.1 Shared API boundary

- Implement a frontend API boundary for the six endpoints/seven interaction
  types.
- Parse approved success DTOs and structured errors.
- Treat IDs as decimal strings.
- Treat Comment timestamp as RFC 3339 UTC.
- Preserve user-entered form data on recoverable errors where practical.
- Do not access persistence directly.

No global state library, routing framework, CSS framework, or other frontend
architecture is mandated by this plan.

### 7.2 Ticket List

- Render `id`, title, priority, assignee, and status only.
- Provide Create and Ticket-detail navigation.
- Provide keyword input and status filter with “All”.
- Send criteria to the backend; do not locally reinterpret search semantics.
- Preserve combined criteria.
- Display loading, empty-list, and no-result states.
- Do not expose pagination or sorting.

### 7.3 Create Ticket

- Provide required title, description, and Priority plus optional assignee.
- Mirror only approved maximum/required/enum validation for usability.
- Do not expose ID, status, Comments, or timestamps.
- Prevent duplicate submission.
- Submit `POST /api/tickets`.
- Navigate to current Ticket detail after 201.
- Display safe validation/network errors.

### 7.4 Ticket Detail

- Display approved Ticket fields and embedded Comments.
- Display only Comment body and timestamp.
- Do not assume Comment ordering.
- Handle empty Comments.
- Provide the ordinary edit, status, and Comment flows when allowed by
  displayed state.

### 7.5 Ordinary edit

- Submit only changed title, description, priority, or assignee.
- Do not submit an empty object.
- Use explicit null to clear assignee.
- Never include status, ID, Comments, or timestamps.
- Refresh current Ticket after success or terminal conflict.

### 7.6 Status actions

Expose exactly:

| Current | Action | Target |
| --- | --- | --- |
| `OPEN` | Start / In Progress | `IN_PROGRESS` |
| `OPEN` | Cancel | `CANCELLED` |
| `IN_PROGRESS` | Resolve | `RESOLVED` |
| `IN_PROGRESS` | Cancel | `CANCELLED` |
| `RESOLVED` | Close | `CLOSED` |

Expose no status action for `CLOSED` or `CANCELLED`. Submit only target status.
After `409 INVALID_STATUS_TRANSITION`, display the lifecycle error and reload
Ticket detail.

### 7.7 Comments

- Submit body only.
- Prevent duplicate submission.
- Reload Ticket detail after success.
- Do not display or submit author/User data.
- Do not assert Comment order.

### 7.8 Terminal state

For `CLOSED` and `CANCELLED`, hide or disable edit, status, and Comment
actions. This is usability only. The service remains authoritative and the UI
must handle `409 TERMINAL_TICKET_CONFLICT`, display a meaningful error, and
refresh current Ticket state.

### 7.9 Error handling

Plan UI behavior for:

- `400 VALIDATION_ERROR`: show validation feedback and preserve form input.
- `404 TICKET_NOT_FOUND`: show missing-Ticket state and list navigation.
- `409 INVALID_STATUS_TRANSITION`: explain stale/invalid transition and
  refresh.
- `409 TERMINAL_TICKET_CONFLICT`: explain terminal restriction and refresh.
- Network/unexpected failure: safe generic error without claiming uncertain
  mutation success.

## 8. Testing Implementation Plan

Implement test work in these streams:

1. **Unit tests:** Service/business rules and complete focused transition
   decisions.
2. **Controller/API tests:** DTO validation, serialization, endpoints, statuses,
   unknown fields, errors, and ordinary/status operation separation.
3. **Repository tests:** H2 relational mappings, Ticket/Comment relationship,
   queries, and approved persistence behavior.
4. **Backend integration tests:** H2 REST → service → repository workflows.
5. **State-machine integration tests:** All 25 current/target pairs through
   REST with persisted-state assertions.
6. **Frontend/UI tests:** Views, action flows, loading/empty/error states,
   duplicate prevention, terminal controls, and stale-state refresh.
7. **PostgreSQL restart verification:** Non-automated acceptance verification
   using the same controlled PostgreSQL database across process restart.

The lifecycle suite must retain:

- 5 allowed cases.
- 20 rejected cases.
- 409 and `INVALID_STATUS_TRANSITION` for every rejected valid-enum pair.
- Full persisted-state comparison after rejection.

Test implementation must follow the approved strategy even if a smaller set
would be easier to generate. Tests verify behavior, not private structure.

## 9. Cross-Cutting Concerns

### 9.1 Validation

- Implement only title/description required and maximum constraints, approved
  Priority values, required Comment body, target status input, operation field
  sets, and API-contract request syntax.
- Do not add minimum lengths, trimming, whitespace rules, assignee format or
  length, or Comment maximum.
- Keep terminal/lifecycle validation in services.

### 9.2 Error handling

- Centralize translation into the four approved error codes.
- Preserve `code`, `message`, `status`, and `path`.
- Never expose stack traces, persistence details, or internal exceptions.

### 9.3 Transactions

- Place transaction boundaries around state-changing service operations.
- Validate current persisted state before committing mutation.
- Ensure rejected transitions and terminal operations do not partially write.
- Do not move lifecycle authorization into database triggers.

### 9.4 Configuration and secrets

- Separate application, test, and local configuration.
- Keep PostgreSQL credentials and other secrets outside version control.
- Use H2 for automated database tests.
- Do not disable transport/security defaults merely for convenience.

### 9.5 Logging

- Log enough context to diagnose operations without exposing secrets or
  sensitive internals.
- Keep internal logging separate from API error messages.
- Do not turn logging into audit/transition history.

## 10. Task Dependency Graph

| Task ID | Task | Depends On | Specification References | Verification |
| --- | --- | --- | --- | --- |
| IMP-001 | Establish Java 21 Spring Boot backend build and layered package boundary | — | TC-001–TC-004; Architecture Sections 2–3 | Backend builds and starts |
| IMP-002 | Establish approved frontend project and three-view structure | — | TC-005; UI Sections 2–4 | Frontend builds and starts |
| IMP-003 | Establish PostgreSQL/H2/local configuration and secret handling | IMP-001 | PR-002–PR-003, TC-003, TC-008; AD-004–AD-005 | Profiles/config load; no secrets committed |
| IMP-004 | Implement Ticket/Comment persistence model, enums, relationship, and indexes | IMP-001, IMP-003 | BR-001–BR-005, PR-001; DM-001–DM-005, DM-007–DM-010 | Persistence context and mapping checks |
| IMP-005 | Implement base Ticket/Comment repositories | IMP-004 | PR-001; Architecture Section 3.3; DM-003 | Basic repository tests |
| IMP-006 | Implement keyword/status/combined repository queries | IMP-005 | FR-009–FR-011, BR-006–BR-008; DM-008 | Search/filter repository tests |
| IMP-007 | Implement request DTOs and approved validation metadata | IMP-001 | VR-002–VR-008; API Sections 5, 8–10 | Deserialization/validation tests |
| IMP-008 | Implement response DTOs, decimal IDs, and Comment timestamp mapping | IMP-001 | ER-004; API Sections 2–3; API-004–API-005 | Serialization contract tests |
| IMP-009 | Implement application exceptions and centralized error response mapping | IMP-007, IMP-008 | ER-001–ER-006; AD-008; API Section 12 | 400/404/409 mapping tests |
| IMP-010 | Implement Ticket create/list/detail/search/filter services | IMP-005, IMP-006, IMP-009 | FR-001–FR-003, FR-009–FR-011, BR-002, BR-006–BR-008 | Service tests |
| IMP-011 | Implement ordinary update and Comment services with terminal enforcement | IMP-005, IMP-009 | FR-004–FR-008, BR-018–BR-020, VR-009; AD-002, AD-007 | Unit tests and no-mutation assertions |
| IMP-012 | Implement dedicated status-transition service | IMP-005, IMP-009 | FR-012, BR-009–BR-017, BR-021; SM-001–SM-010 | Complete focused decision tests |
| IMP-013 | Implement Ticket create/list/detail/update/status controllers | IMP-009–IMP-012 | FR-001–FR-007, FR-009–FR-013; API Sections 5–9 | Controller/API tests |
| IMP-014 | Implement Ticket Comment controller | IMP-009, IMP-011 | FR-008; API Section 10 | Comment API tests |
| IMP-015 | Complete repository/data tests | IMP-005, IMP-006 | Test Strategy 3.3, 7, 9 | Repository suite passes on H2 |
| IMP-016 | Complete service/business unit tests | IMP-010–IMP-012 | Test Strategy 3.1, 5, 8 | Business suite passes |
| IMP-017 | Complete controller/API contract tests | IMP-013, IMP-014 | Test Strategy 3.2, 6, 13 | Contract suite passes |
| IMP-018 | Complete general backend integration tests | IMP-013–IMP-015 | Test Strategy 3.4, 4, 9 | REST/H2 workflows pass |
| IMP-019 | Complete 25-case state-machine integration suite | IMP-012, IMP-013, IMP-015 | TC-011, AC-020–AC-021; TS-D-001, TS-D-004 | 5 allowed and 20 rejected cases pass with DB assertions |
| IMP-020 | Implement frontend API boundary and Ticket List against the frozen API contract | IMP-002 | UI Sections 3.1, 5, 12; UI-INV-001–UI-INV-002, UI-INV-005–UI-INV-007 | List/search/filter UI tests with controlled API responses |
| IMP-021 | Implement Create Ticket view and navigation | IMP-002, IMP-020 | FR-001, AC-001; UI Section 3.2; UI-D-002 | Create UI tests with controlled API responses |
| IMP-022 | Implement Ticket Detail and ordinary edit flow | IMP-020 | FR-003–FR-008; UI Sections 3.3–3.4 | Detail/edit UI tests with controlled API responses |
| IMP-023 | Implement status, Comment, terminal controls, errors, and stale refresh | IMP-022 | UI Sections 3.5–3.6, 7, 10, 13; UI-INV-012–UI-INV-017 | Status/Comment/error UI tests with controlled API responses |
| IMP-024 | Complete frontend/UI and usability-baseline test suite | IMP-020–IMP-023 | UI Sections 14–15; Test Strategy 3.6, 14; UI-INV-001–UI-INV-021 | UI suite and approved usability checks pass |
| IMP-025 | Perform PostgreSQL restart/durability acceptance verification | IMP-003, IMP-013, IMP-018 | PR-004, AC-015; Test Strategy 9.3, TS-D-003 | Data survives process restart with same database |
| IMP-027 | Complete developer-facing README and record actual AI-assisted engineering/tool usage | IMP-001–IMP-025 | Documentation rule; TC-006–TC-007 | README covers required setup, architecture, testing, decisions, limitations, and actual Cursor/Copilot usage |
| IMP-026 | Run full backend/frontend integration verification and specification/code review | IMP-016–IMP-025, IMP-027 | All frozen specifications; review commands | Integrated evidence passes; confirmed defects resolved |

Dependencies are intentionally selective. For example, DTO work can proceed
beside persistence after foundation, repository queries depend on base
repositories, and frontend foundation can proceed before backend completion
while final API integration verification waits for the relevant backend
operations. IMP-027 is completed before IMP-026 so the final review includes
required documentation; IMP-027 does not depend on IMP-026.

## 11. Recommended Implementation Order

1. Establish backend and frontend foundations (`IMP-001`, `IMP-002`).
2. Establish database/test configuration (`IMP-003`).
3. Implement persistence mappings and base repositories (`IMP-004`,
   `IMP-005`).
4. Implement request/response DTOs and centralized errors in parallel with
   repository query work (`IMP-006`–`IMP-009`).
5. Implement read/create/search services, then mutation services and lifecycle
   enforcement (`IMP-010`–`IMP-012`).
6. Implement only the approved controllers (`IMP-013`, `IMP-014`).
7. Complete repository, service, API, general integration, and complete matrix
   testing (`IMP-015`–`IMP-019`).
8. Integrate the frontend API boundary and views (`IMP-020`–`IMP-023`).
9. Complete UI verification (`IMP-024`).
10. Perform PostgreSQL restart verification (`IMP-025`).
11. Complete required developer documentation (`IMP-027`).
12. Run full review, fix confirmed defects, and rerun evidence (`IMP-026`).

This sequence puts frozen contracts and authoritative backend behavior before
dependent UI integration, while permitting independent foundation and DTO
work to reduce idle time.

## 12. Incremental Verification Checkpoints

### Checkpoint A — Foundation

Required evidence:

- Backend and frontend build/start commands succeed.
- Java 21 is active.
- H2 test setup initializes.
- PostgreSQL values can be supplied externally.
- Secret scan/review finds no committed credentials.

Do not proceed with dependent implementation if the selected project structure
cannot preserve approved layering.

### Checkpoint B — Persistence

Required evidence:

- Ticket and Comment mappings initialize on H2.
- Generated identifiers, enum text, nullable assignee, timestamp, and
  relationship persist/retrieve.
- Approved relationship/index mappings exist.
- No unsupported entity or field exists.

### Checkpoint C — Contracts

Required evidence:

- Request and response JSON match the API contract.
- IDs are decimal strings externally.
- Comment timestamp is RFC 3339 UTC.
- Unknown fields are rejected.
- Structured error fields and codes map correctly.
- Persistence entities are not serialized.

### Checkpoint D — Business Rules

Required evidence:

- Creation assigns `OPEN`.
- Focused tests cover five allowed and 20 rejected decisions.
- Same-state rejection passes.
- Both terminal statuses reject all approved mutations.
- Rejection does not initiate unintended mutation.

### Checkpoint E — Backend API

Required evidence:

- Exactly six endpoint operations exist.
- All success/status/error mappings pass controller and integration tests.
- Ordinary PATCH cannot change status.
- Dedicated status operation receives only target status.
- Comment endpoint accepts body only.

### Checkpoint F — State Machine

Required evidence:

- All 25 REST/database cases execute.
- Five cases persist target state.
- Twenty cases return 409/`INVALID_STATUS_TRANSITION`.
- Persisted status and unrelated Ticket data are unchanged after each
  rejection.

### Checkpoint G — Search and Filtering

Required evidence:

- Case-insensitive partial title and description cases pass.
- OR semantics pass.
- Every exact status passes.
- Four combined predicate combinations prove AND.
- Empty keyword and empty result behavior pass.

### Checkpoint H — Frontend

Required evidence:

- Three primary views and detail action flows operate against backend APIs.
- Loading/empty/error states pass.
- Exactly five lifecycle actions are available.
- Terminal controls and stale-state refresh pass.
- UI makes no direct database call.

### Checkpoint I — Full Verification

Required evidence:

- All approved automated suites pass.
- PostgreSQL restart/durability acceptance verification passes.
- AC-001 through AC-022 have linked evidence.
- No secrets or out-of-scope capabilities are present.
- Code review finds no unresolved specification violation.

## 13. Specification Traceability

| Implementation ownership | Tasks | Frozen references | Acceptance contribution |
| --- | --- | --- | --- |
| Foundation and technology constraints | IMP-001–IMP-003 | TC-001–TC-005, TC-008, PR-002–PR-003, AD-004–AD-005 | Verification support for AC-022 |
| Ticket/Comment data model | IMP-004–IMP-005 | BR-001–BR-005, PR-001, DM-001–DM-005, DM-007–DM-010 | Persistence support for AC-006 |
| API representations/errors | IMP-007–IMP-009 | ER-001–ER-006, API-004–API-007, AD-006, AD-008 | Backend support for AC-016–AC-018 |
| Search/filter persistence | IMP-006, IMP-010, IMP-015 | FR-009–FR-011, BR-006–BR-008, DM-008 | Backend support for AC-007–AC-009 |
| Create/list/detail services | IMP-010 | FR-001–FR-003, BR-002 | Backend support for AC-001–AC-003 |
| Ordinary update service/API | IMP-011, IMP-013, IMP-017–IMP-018 | FR-004–FR-007, BR-018, BR-020, VR-002–VR-006, VR-009, TC-009 | Backend support for AC-004–AC-005, AC-014, AC-016 |
| Comment service/API | IMP-011, IMP-014, IMP-018 | FR-008, BR-005, BR-019, VR-007, VR-009, DM-003, DM-007 | Backend support for AC-006, AC-014, AC-016 |
| Lifecycle service/API | IMP-012–IMP-013, IMP-016–IMP-019 | FR-012, BR-009–BR-017, BR-021, VR-008, ER-003, PR-005, TC-010–TC-011, SM-001–SM-010 | Implements AC-011–AC-013 and AC-020–AC-021; backend support for AC-010 |
| API controllers | IMP-013–IMP-014 | FR-013, TC-004, API Sections 4–13 | Backend contract support for AC-001–AC-014, AC-016–AC-018 |
| Frontend list/create/detail | IMP-020–IMP-022 | TC-005, UI-INV-001–UI-INV-011, UI-D-001–UI-D-004 | UI ownership for AC-001–AC-009 |
| Frontend lifecycle/terminal/error flows | IMP-023–IMP-024 | UI Sections 7, 14–15; UI-INV-012–UI-INV-021, UI-D-005–UI-D-008 | UI ownership for AC-010, AC-012–AC-014, AC-017, AC-019 |
| Persistence verification | IMP-015, IMP-018–IMP-019, IMP-025 | PR-001–PR-005, TS-D-003–TS-D-004 | Evidence for AC-013, AC-015, AC-020–AC-021 |
| Required project/AI documentation | IMP-027 | Documentation rule; TC-006–TC-007 | Process evidence |
| Full review and security constraint | IMP-026 | TC-008, AC-022; all specifications | Final evidence review for AC-001–AC-022 |

This mapping assigns every major acceptance criterion jointly to the backend,
frontend, persistence, and verification work needed to prove it. Backend rows
marked “support” do not independently satisfy UI acceptance criteria.

## 14. Risks and Implementation Traps

| Trap | Consequence | Prevention/review checkpoint |
| --- | --- | --- |
| Business logic in controllers | Rules become inconsistent and hard to test | Review controllers at Checkpoint E; require service delegation |
| JPA entities exposed as responses | Persistence leaks into contract | DTO serialization review at Checkpoint C |
| Status accepted by ordinary PATCH | Lifecycle bypass | Reject unknown/status field; contract and integration tests |
| Client supplies authoritative current status | Stale or forged transitions | Status DTO contains target only; service loads persisted Ticket |
| Only five success transitions implemented/tested | Invalid pairs may slip through | Closed-set rule plus mandatory 25-case Checkpoint F |
| Same-state treated as no-op | Violates BR-015 | Include five diagonal matrix cases |
| Terminal edits or Comments allowed | Violates BR-018/BR-019 | Service enforcement and both-state integration matrix |
| Incorrect 400/404/409 mapping | Contract/UI mismatch | Centralized errors and controller/API suite |
| Unknown JSON silently accepted | Unsupported input enters service | Strict DTO deserialization tests for every write request |
| Numeric JSON identifiers | JavaScript precision risk and API violation | Decimal-string serialization tests at Checkpoint C |
| Comment timestamp not RFC 3339 UTC | Client interoperability failure | Serialization test with `Z`; no Ticket timestamps |
| Unsupported fields/entities added | Scope and schema drift | Data-model review at Checkpoint B |
| Case-sensitive or exact keyword search | Violates BR-006 | Repository predicate tests at Checkpoint G |
| Combined query implemented with OR | Returns incorrect Tickets | Four-way predicate data at Checkpoint G |
| UI controls treated as authorization | Direct API calls bypass rules | Backend unit/integration enforcement; stale-state UI test |
| H2 treated as PostgreSQL durability proof | AC-015 remains unverified | PostgreSQL process-restart evidence at Checkpoint I |
| 409 asserted without persisted-state check | Partial mutation can go unnoticed | Full state comparison in IMP-019 |
| Elasticsearch/Redis added for search | Unnecessary infrastructure and scope | Reject dependency in foundation/repository review |
| Pagination or sorting added | Contract drift | Endpoint/query and UI review |
| Authentication introduced | New product behavior | Dependency and endpoint review |
| Audit fields/history added | Data-model drift | Mapping review; only Comment timestamp permitted |
| Tests assert implementation details | Fragile suite that misses behavior | Trace tests to requirements and contract outcomes |
| Comment array order assumed | Invented sorting guarantee | Do not sort/assert order; API-003 review |
| Framework default adds endpoint/field | Accidental public contract expansion | Enumerate exposed routes and serialized fields at Checkpoint E |

AI coding agents must be instructed with the relevant task ID and frozen
references, limited to the task's files, and required to report any ambiguity
instead of creating a plausible but unapproved behavior.

## 15. Definition of Done

Implementation is done only when:

- Backend and frontend implementation match all frozen specifications.
- Exactly the six approved endpoint operations are implemented.
- All approved request and business validation is enforced by the backend.
- No extra validation is introduced.
- The five-state/five-transition state machine is authoritative in services.
- All terminal restrictions are enforced.
- Ticket and Comment persistence and relationship work.
- Search and filtering match approved OR/AND semantics.
- All three UI views and detail action flows work.
- Structured errors and UI error behavior conform.
- Unexpected-server-failure behavior has been resolved through SDD review and
  implemented according to the resulting approved API contract.
- All automated suites pass; automated database tests use H2.
- All 25 lifecycle integration cases pass: five allowed and 20 rejected.
- Rejected transitions preserve complete persisted Ticket state.
- Terminal rejected operations leave persisted data unchanged.
- PostgreSQL restart verification is complete.
- AC-001 through AC-022 have evidence.
- No secret is committed.
- No out-of-scope endpoint, entity, field, state, transition, view, or product
  capability was added.
- Code review and specification-consistency review are complete.
- Confirmed critical/major specification deviations are resolved and affected
  verification reruns successfully.

No arbitrary code-coverage percentage is part of Definition of Done.

## 16. Implementation Constraints

- No new endpoint.
- No new state or transition.
- No new persistent entity or field.
- No new product capability.
- No extra validation rule.
- No change to approved API semantics.
- No change to terminal-state behavior.
- No bypass of backend enforcement.
- No implementation decision may silently override a frozen specification.
- No dependency may be added only because it is commonly used.

If implementation reveals a genuine contradiction or missing requirement:

1. Stop the affected implementation task.
2. Document the issue and impacted requirement/task.
3. Review the authoritative specification.
4. Update it only through the SDD review process if a change is approved.
5. Update dependent specification artifacts and traceability.
6. Resume implementation only from the revised approved baseline.

Specification conflicts must never be silently resolved in code.

## 17. Unresolved Implementation Decisions

### 17.1 Blocking specification gap

The project API standards require the API specification to define behavior for
unexpected server failures. The frozen API contract defines the approved 400,
404, and 409 categories and the UI defines a generic unexpected error state,
but no global unexpected-error HTTP response is defined. This is a
**specification/standards consistency gap**. The product requirements do not
require a fifth error category.

This is not an implementation choice. Before implementing centralized handling
for unexpected server failures:

1. Stop that portion of IMP-009.
2. Review the gap through the SDD process.
3. Update the API contract and dependent artifacts only if approved.
4. Resume implementation from the revised frozen contract.

The existing approved error mappings can be implemented independently. No new
error code or HTTP status may be invented by this plan.

### 17.2 Open implementation choices

The following implementation choices remain open and must be resolved without
changing observable behavior:

- Exact Spring Boot version compatible with Java 21.
- Backend build tool and detailed package naming.
- Exact frontend choice within React, Next.js, or approved equivalent.
- Frontend routing, state-management, styling, and component organization
  needed to realize the three approved views.
- Exact test frameworks and test lifecycle mechanisms.
- Database migration/versioning mechanism.
- Local PostgreSQL provisioning approach.
- Non-automated PostgreSQL restart-verification environment provisioning.
- Exact configuration-file organization and environment variable names.
- Exact transaction annotations/configuration that preserve service-level
  boundaries.
- Exact implementation of portable case-insensitive partial queries.

Resolution criteria:

- Prefer the smallest maintainable choice.
- Avoid unnecessary dependency/infrastructure.
- Preserve PostgreSQL/H2 constraints.
- Keep API and behavior unchanged.
- Record material implementation choices in the appropriate implementation
  documentation or review, not as new product requirements.

No frozen choice may be reopened, including identifiers, entities, field sets,
enum values, endpoints, error codes, lifecycle rules, views, or matrix
coverage.

## 18. Consistency Review

The plan was reviewed against all frozen specifications:

- **Requirements:** FR-001–FR-013 and AC-001–AC-022 have implementation and
  verification ownership.
- **Architecture:** Controller, service, repository, persistence, DTO, and
  error responsibilities remain separated.
- **Data model:** Only Ticket and Comment and their approved fields,
  relationship, enums, and indexes are planned.
- **API contract:** Exactly six endpoint operations, approved DTO semantics,
  decimal-string IDs, timestamp, errors, and queries are planned.
- **State machine:** Exactly five states, five allowed transitions, and 20
  rejected pairs are planned with no-mutation verification.
- **UI flow:** Exactly three primary views and approved detail-context flows
  are planned.
- **Test strategy:** Test-level ownership, complete matrix coverage, H2
  automation, and PostgreSQL acceptance evidence are preserved.

Self-review findings:

- **Contradictions/gaps:** The unexpected-server-failure contract gap in
  Section 17.1 must be resolved through SDD review before that handling is
  implemented. No contradiction was found among the frozen product
  specifications themselves.
- **Missing implementation coverage:** None identified for approved
  acceptance criteria; unexpected-failure implementation remains intentionally
  blocked pending specification.
- **Wrong-layer assignments:** None identified; lifecycle and terminal rules
  remain in services.
- **New functionality:** None introduced.
- **Missing dependencies:** Task graph includes foundation, persistence,
  contracts, repositories, services, controllers, tests, frontend, PostgreSQL
  verification, and review dependencies.
- **Unnecessary complexity:** No external search, messaging, authentication,
  pagination, sorting, audit system, or additional architecture is planned.
- **Pre-coding risks:** Resolve the open technology/tooling choices in Section
  17 while preserving frozen behavior, and resolve Section 17.1 before
  unexpected-failure handling.

Potential AI misinterpretations requiring explicit guardrails:

- Do not infer common fields such as Ticket timestamps, creator, author, or
  audit columns.
- Do not convert decimal-string API IDs to JSON numbers.
- Do not add status to ordinary update DTOs for convenience.
- Do not treat same-state requests as successful idempotent updates.
- Do not make Comments available on terminal Tickets.
- Do not assume Comment order.
- Do not add frontend-only rules or trust frontend status.
- Do not reduce matrix tests to representative examples.
- Do not claim H2 proves PostgreSQL restart durability.
- Do not resolve an unspecified implementation choice by changing an
  observable contract.
