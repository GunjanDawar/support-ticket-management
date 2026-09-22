# Support Ticket Management System Architecture Specification

## 1. Purpose and Scope

This document defines the high-level architecture of the Support Ticket
Management System. It translates the approved requirements in
`spec/requirements.md` into system boundaries, responsibilities, dependency
rules, and architectural decisions.

This specification does not add functional requirements. It does not define
detailed endpoint paths, request or response schemas, database tables or
entity fields, a standalone state machine, detailed UI flows, or a detailed
test strategy. Those details are deferred to their respective specification
documents.

## 2. Architecture Overview

The system uses a client-server architecture with four major runtime parts:

1. **Frontend:** A React, Next.js, or equivalent web frontend provides the
   user interface. It presents ticket data, gathers user input, calls the REST
   API, and displays operation results and meaningful errors.
2. **Backend:** A Java 21 Spring Boot application exposes the REST API and is
   the authoritative owner of validation, business rules, ticket lifecycle
   enforcement, and persistence coordination.
3. **Application database:** PostgreSQL stores durable application data and
   preserves it across application restarts.
4. **Automated-test database:** H2 provides the database used by automated
   tests, including integration tests.

The frontend communicates with the backend only through the documented REST
API. It does not access the database or backend persistence models directly.
The backend accesses PostgreSQL or H2 through its repository/data-access
boundary.

### 2.1 Major boundaries

| Part | Responsibility | Boundary |
| --- | --- | --- |
| Frontend | User interaction, client-side presentation, API invocation, and meaningful error display | Depends on the REST contract, not backend classes or database structures |
| REST backend | HTTP handling, validation, business rules, lifecycle enforcement, and persistence coordination | Exposes DTO-based API contracts and owns all authoritative decisions |
| PostgreSQL | Durable application persistence | Accessed by the backend data-access layer only |
| H2 | Persistence for automated tests | Replaces the application database within test execution; it is not the application persistence store |

### 2.2 Typical request and response flow

For a successful operation:

1. The user initiates an action in the UI.
2. The frontend validates only what is useful for immediate feedback and sends
   a request to the REST API.
3. The controller/API layer parses the request, applies request-level
   validation, and delegates to the service layer.
4. The service layer loads required state through a repository, applies
   business and lifecycle rules, and coordinates the operation.
5. The repository/data-access layer reads or writes the configured database.
6. The service returns a result to the controller.
7. The controller maps the result to an API response DTO and HTTP response.
8. The frontend updates its displayed state from the response.

For a failed operation:

1. Request validation, resource lookup, business validation, or lifecycle
   validation identifies the failure.
2. The backend raises or returns a typed application error without committing
   an invalid state change.
3. Centralized exception handling maps the error to the specified HTTP status
   and structured JSON error response.
4. The frontend interprets the error contract and displays a meaningful
   user-facing message.

## 3. Backend Architecture

The backend uses a layered architecture:

`Controller/API → Service/Business → Repository/Data Access → Database`

DTOs form the external API boundary. Domain and persistence representations
remain internal to the backend. Centralized error handling crosses the API
boundary in a controlled and consistent way.

### 3.1 Controller/API layer

**Responsibilities**

- Receive HTTP requests and produce HTTP responses.
- Bind request data to request DTOs.
- Trigger request/input validation.
- Delegate operations to the service layer.
- Map successful service results to response DTOs.
- Preserve the separation between ordinary field updates and status
  transitions.

**May depend on**

- Request and response DTOs.
- Service interfaces or service components.
- Validation facilities.
- API error-handling abstractions.

**Must not be responsible for**

- Ticket lifecycle decisions.
- Terminal-ticket modification rules.
- Database access or transaction orchestration.
- Persistence entity manipulation.
- Core search, filtering, or other business semantics.

### 3.2 Service/business layer

**Responsibilities**

- Implement and coordinate ticket use cases.
- Enforce business rules and terminal-state restrictions.
- Authoritatively validate ticket status transitions.
- Coordinate repositories for reads and writes.
- Define transaction boundaries for state-changing operations.
- Ensure invalid operations do not modify persisted state.
- Apply search and filter semantics through appropriate repository queries or
  query abstractions.

**May depend on**

- Domain and persistence abstractions.
- Repository interfaces.
- Application error types.
- Transaction management supplied by the backend framework.

**Must not be responsible for**

- HTTP request parsing or UI presentation.
- Returning frontend components.
- Depending on controller implementations.
- Embedding database-specific transport concerns in business rules.

### 3.3 Repository/data-access layer

**Responsibilities**

- Provide persistence operations needed by the service layer.
- Retrieve tickets and related persisted data.
- Persist valid changes.
- Support case-insensitive partial title/description search, exact status
  filtering, and their combination as required.
- Isolate database access details from controllers and services.

**May depend on**

- Persistence mappings and database-access framework facilities.
- Domain or persistence representations established by the future data-model
  specification.

**Must not be responsible for**

- Deciding whether status transitions are valid.
- Deciding whether terminal tickets may be edited or commented on.
- HTTP response construction.
- UI-specific formatting.

### 3.4 Persistence/domain layer

**Responsibilities**

- Represent ticket state and the values required by approved requirements.
- Preserve ticket identity and relationships needed for persistence.
- Represent status and priority values consistently.
- Support storage and retrieval without leaking persistence representation
  into the public API.

**May depend on**

- Java and persistence mechanisms selected for the Spring Boot backend.
- Domain rules that are intrinsic to the represented values.

**Must not be responsible for**

- HTTP concerns.
- UI concerns.
- Detailed request/response contracts.

The exact entity structure, field types, relationships, constraints, and table
schema are deferred to `spec/data-model.md`.

### 3.5 DTO/request-response layer

**Responsibilities**

- Define API-facing request and response shapes.
- Separate creation, ordinary field update, status transition, comment, query,
  and response concerns where their semantics differ.
- Carry only data allowed by the applicable operation.
- Prevent ordinary update requests from accepting status changes.
- Provide a stable boundary independent of persistence entities.

**May depend on**

- API contract definitions and request-validation metadata.
- API-safe value representations.

**Must not be responsible for**

- Persistence operations.
- Business-rule enforcement.
- Database schema design.

Detailed DTO fields and payload schemas are deferred to
`spec/api-contract.md`.

### 3.6 Exception/error-handling layer

**Responsibilities**

- Centralize translation of known application failures into HTTP responses.
- Produce the required JSON error structure containing `code`, `message`,
  `status`, and `path`.
- Map input-validation failures to `400 Bad Request`.
- Map missing tickets to `404 Not Found`.
- Map invalid status transitions to `409 Conflict`.
- Prevent internal exception details and stack traces from becoming API
  responses.

**May depend on**

- Typed validation, not-found, conflict, and application exceptions.
- Controller-framework exception-handling facilities.

**Must not be responsible for**

- Making lifecycle decisions.
- Mutating ticket data.
- Recovering invalid requests by silently changing their meaning.

## 4. Frontend Architecture

The frontend is an API client and presentation layer. It may provide
client-side validation and restrict obviously unavailable actions for user
experience, but it is not authoritative for business rules.

The high-level frontend responsibilities are:

- **Ticket list:** Request and render the ticket collection.
- **Ticket creation:** Collect required creation input, provide immediate
  input feedback, submit the request, and present success or failure.
- **Ticket details:** Request and present one ticket and its available
  information.
- **Ticket editing:** Allow title, description, priority, and assignee changes
  for non-terminal tickets and submit them through the ordinary update
  operation.
- **Comments:** Collect a required comment body, submit it for a non-terminal
  ticket, and display comments returned by the backend.
- **Search:** Collect a keyword and invoke the API's case-insensitive partial
  title/description search behavior.
- **Status filtering:** Select and submit an exact status filter.
- **Combined query:** Preserve both keyword and status criteria when the user
  applies them together.
- **Status transition UI:** Present status-change actions separately from
  ordinary ticket editing and invoke the dedicated transition operation.
- **Error display:** Interpret structured backend errors and show meaningful
  feedback associated with the failed operation.

Detailed routes, screen layouts, component hierarchy, styling, loading states,
and interaction sequences are deferred to `spec/ui-flow.md`.

## 5. State-Machine Architecture

The service/business layer is the authoritative enforcement point for the
ticket lifecycle. A status-transition service operation shall:

1. Load the current persisted ticket.
2. Compare the current status with the requested target status.
3. Accept only one of the following transitions:
   - `OPEN → IN_PROGRESS`
   - `IN_PROGRESS → RESOLVED`
   - `RESOLVED → CLOSED`
   - `OPEN → CANCELLED`
   - `IN_PROGRESS → CANCELLED`
4. Reject every other transition, including a transition to the current
   status.
5. Persist the new status only after validation succeeds.

`CLOSED` and `CANCELLED` are terminal statuses and therefore have no allowed
outgoing transition.

The frontend may hide or disable unavailable transitions, but that behavior is
advisory. It cannot be trusted as enforcement because API requests can be sent
without the frontend, frontend state can be stale, and clients can be modified.
Backend service enforcement ensures the same lifecycle rules apply to every
client and keeps rejected requests from changing the database.

The detailed lifecycle model and transition cases are deferred to
`spec/state-machine.md`.

## 6. Validation Architecture

Validation is divided into three categories with different enforcement
locations.

### 6.1 Request/input validation

The controller boundary validates the shape and basic constraints of incoming
DTOs before invoking business operations:

- Title is required and has at most 200 characters.
- Description is required and has at most 5,000 characters.
- A supplied priority is one of `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- Comment body is required.

The frontend may repeat these checks for immediate feedback. Backend
validation remains authoritative.

### 6.2 Business-rule validation

The service layer validates rules that depend on operation meaning or current
persisted state:

- A ticket in `CLOSED` or `CANCELLED` cannot have its title, description,
  priority, or assignee updated.
- A comment cannot be added to a ticket in `CLOSED` or `CANCELLED`.
- Ordinary update operations are limited to title, description, priority, and
  assignee and cannot change status.
- Search and filter behavior follows the approved matching semantics.

### 6.3 State-transition validation

The service layer validates current status against requested target status
using the exact transition set in Section 5. This validation is separate from
ordinary field-update validation and is reached only through the dedicated
status-transition operation.

Input validation prevents malformed or constraint-violating requests.
Business validation determines whether a well-formed operation is allowed in
the current context. State-transition validation is the lifecycle-specific
form of business validation that protects ticket status.

## 7. Error-Handling Architecture

Backend failures use a centralized error-handling boundary and a consistent
JSON representation:

- `code`: stable machine-readable error category.
- `message`: human-readable description.
- `status`: HTTP status associated with the failure.
- `path`: request path on which the failure occurred.

Required mappings are:

- Input-validation failure: `400 Bad Request`.
- Nonexistent ticket: `404 Not Found`.
- Invalid status transition: `409 Conflict`.

The detailed error codes and endpoint-specific cases are deferred to
`spec/api-contract.md`. In particular, the approved requirements do not yet
assign an HTTP status to every possible business-rule failure.

The frontend API boundary shall make structured errors available to the
feature that initiated the operation. The UI shall use the response to present
a meaningful message rather than failing silently. Internal stack traces,
database details, and implementation exceptions shall not be exposed to the
user.

## 8. Persistence Architecture

The backend is the only component that interacts with persistence:

- PostgreSQL is the application database.
- H2 is the automated-test database.
- Services coordinate persistence through repositories.
- Controllers and the frontend do not access either database directly.

### 8.1 Repository boundary

Repositories abstract storage and retrieval operations needed by ticket use
cases. They provide data access without owning lifecycle or terminal-state
decisions. Database-specific query implementation remains behind this
boundary.

### 8.2 Transaction boundaries

State-changing use cases are transaction boundaries at the service layer.
Validation that depends on current persisted state occurs before a valid
change is committed. If lifecycle or business validation fails, the operation
must complete without persisting a partial change.

For a status transition, loading the current ticket, validating the
transition, and persisting an accepted status change form one logical
transaction. Rejected transitions perform no status write and leave all
persisted ticket state unchanged.

### 8.3 Durability

PostgreSQL stores committed application data independently of the Spring Boot
process. Restarting the application and reconnecting it to the same database
therefore preserves ticket data. Database deployment, backup, and operational
recovery details are outside this specification.

Detailed entities, tables, columns, keys, and relationships are deferred to
`spec/data-model.md`.

## 9. API Architecture

The Spring Boot backend exposes REST operations for:

- Creating tickets.
- Listing tickets.
- Viewing ticket details.
- Updating title, description, priority, and assignee.
- Adding comments.
- Searching by keyword.
- Filtering by exact status.
- Combining search and status filtering.
- Performing ticket status transitions.

Ordinary ticket updates use `PATCH` and accept only title, description,
priority, and assignee changes. They must not accept or indirectly change
status. Status transitions use a dedicated operation and endpoint so that
lifecycle intent, validation, and error behavior remain distinct from ordinary
field updates.

Controllers expose these operations through DTOs and delegate to services.
The API does not expose persistence entities directly.

Detailed resource paths, payloads, response bodies, success statuses, and HTTP
examples are deferred to `spec/api-contract.md`.

## 10. Testing Architecture

Testing is organized by architectural boundary:

- **Business-logic unit tests:** Exercise service decisions, lifecycle rules,
  terminal restrictions, search/filter coordination, and validation behavior
  in isolation where appropriate.
- **Controller/API tests:** Verify request validation, delegation, HTTP
  mapping, and structured errors at the REST boundary where appropriate.
- **Repository/database tests:** Verify persistence queries and database
  behavior using H2.
- **Integration tests:** Exercise complete backend flows across the REST API,
  service layer, repository, and H2 database.

State-machine integration tests shall call the REST API for every allowed
transition and verify the resulting database state. They shall also call the
API for invalid transitions, including same-status transitions, and verify
both rejection and unchanged database state.

Backend validation tests shall cover required title, title length, required
description, description length, priority values, required comment body,
terminal modification restrictions, and lifecycle validation.

Exact test suites, test data, coverage boundaries, and tooling details are
deferred to `spec/test-strategy.md`.

## 11. Dependency and Responsibility Rules

The following rules govern implementation structure:

1. Controllers handle HTTP concerns and delegate business operations.
2. Controllers do not contain core lifecycle or terminal-state rules.
3. Controllers do not access databases or repositories directly.
4. Services own business logic and coordinate transaction boundaries.
5. Repositories own persistence and query access, not business decisions.
6. API DTOs are separate from persistence representations.
7. Persistence entities are not exposed directly through the REST API.
8. Dependencies flow from controller to service to repository; lower layers
   do not depend on controllers or frontend code.
9. The backend is authoritative for input, business, and lifecycle validation.
10. Frontend controls do not replace server-side rule enforcement.
11. Ordinary `PATCH` updates cannot accept or change status.
12. Status transitions pass through the dedicated transition operation.
13. Errors are translated at a centralized API boundary.
14. Invalid state changes are not persisted.
15. No layer may hard-code or commit secrets.

These rules establish separation of concerns without requiring additional
frameworks, services, or infrastructure.

## 12. Architectural Decisions and Rationale

### AD-001 — Layered backend

- **Type:** Architectural choice for maintainability and testability.
- **Decision:** Organize the backend into controller, service, repository,
  persistence/domain, DTO, and error-handling responsibilities.
- **Rationale:** The separation keeps HTTP, business, and persistence concerns
  independently understandable and testable while supporting all required
  backend capabilities.

### AD-002 — Backend-owned rule enforcement

- **Type:** Requirement-driven decision.
- **Decision:** Services authoritatively enforce business rules and the ticket
  lifecycle.
- **Rationale:** Requirements mandate backend validation, rejection of invalid
  transitions, and preservation of persisted state. Frontend-only enforcement
  cannot guarantee those outcomes for every API client.

### AD-003 — Separate status transition operation

- **Type:** Requirement-driven decision.
- **Decision:** Keep status transitions separate from ordinary `PATCH`
  updates.
- **Rationale:** BR-020, BR-021, TC-009, and TC-010 require the separation. It
  makes lifecycle intent explicit and prevents field updates from bypassing
  transition validation.

### AD-004 — PostgreSQL application persistence

- **Type:** Requirement-driven technology decision.
- **Decision:** Use PostgreSQL for application persistence.
- **Rationale:** PR-002 explicitly selects PostgreSQL, and its process-
  independent durable storage supports restart durability.

### AD-005 — H2 automated-test persistence

- **Type:** Requirement-driven technology decision.
- **Decision:** Use H2 for automated database tests.
- **Rationale:** PR-003 requires H2, and it provides an isolated database for
  repeatable automated execution.

### AD-006 — DTO/persistence separation

- **Type:** Architectural choice for maintainability and contract isolation.
- **Decision:** Use request and response DTOs rather than exposing persistence
  entities through the REST API.
- **Rationale:** API contracts and database representations change for
  different reasons. Separating them prevents persistence concerns from
  defining the public API and supports operation-specific validation.

### AD-007 — Service-level transaction boundaries

- **Type:** Architectural choice supporting requirement enforcement.
- **Decision:** Place transaction boundaries around state-changing service
  operations.
- **Rationale:** Business validation and persistence must act as one logical
  operation so rejected transitions and failed updates do not leave partial
  state.

### AD-008 — Centralized error translation

- **Type:** Architectural choice supporting explicit error requirements.
- **Decision:** Translate application failures into structured HTTP errors at
  one backend boundary.
- **Rationale:** Centralization provides consistent `code`, `message`,
  `status`, and `path` fields and prevents implementation details from leaking.

## 13. Requirement Traceability

| Architecture area or decision | Requirement trace | Classification |
| --- | --- | --- |
| Web frontend boundary | FR-001–FR-012, ER-005, TC-005 | Requirement-driven boundary |
| Spring Boot REST backend | FR-013, TC-001, TC-002, TC-004 | Requirement-driven technology and interface |
| Layered backend | Supports FR-001–FR-013, BR-002–BR-021, VR-001–VR-009 | AD-001 architectural choice |
| Backend business-rule authority | VR-001, VR-008, VR-009, ER-006 | AD-002 requirement-driven |
| Allowed lifecycle transitions | BR-009–BR-013, AC-011 | Requirement-driven |
| Rejection of unlisted and same-status transitions | BR-014, BR-015, ER-003, AC-012 | Requirement-driven |
| Terminal status and modification restrictions | BR-016, BR-018, BR-019, VR-009, AC-014 | Requirement-driven |
| Atomic rejected transitions | BR-017, PR-005, AC-013 | Requirement-driven |
| Request validation boundary | VR-001–VR-007, ER-002, AC-016 | Requirement-driven |
| Search and filtering support | FR-009–FR-011, BR-006–BR-008, AC-007–AC-009 | Requirement-driven |
| Centralized structured errors | ER-001–ER-006, AC-017–AC-019 | AD-008 supports explicit requirements |
| PostgreSQL application persistence | PR-001, PR-002, PR-004, TC-003, AC-015 | AD-004 requirement-driven |
| H2 automated-test persistence | PR-003, TC-003, TC-011 | AD-005 requirement-driven |
| Repository boundary | PR-001–PR-005 | AD-001 architectural choice supporting persistence |
| Service transaction boundaries | BR-017, PR-005, AC-013 | AD-007 architectural choice supporting atomicity |
| DTO/API boundary | FR-013, ER-004, TC-004 | AD-006 architectural choice |
| Ordinary `PATCH` updates | BR-020, TC-009 | Requirement-driven |
| Dedicated status operation | BR-021, TC-010 | AD-003 requirement-driven |
| REST/database state-machine integration tests | TC-011, AC-020, AC-021 | Requirement-driven |
| No committed secrets | TC-008, AC-022 | Requirement-driven constraint |

## 14. Assumptions and Deferred Questions

No new functional assumptions are introduced. The following approved choices
or details remain intentionally deferred:

- The exact frontend technology within the allowed React, Next.js, or
  equivalent choice.
- Detailed REST endpoint paths, payloads, responses, success statuses, and
  endpoint-specific errors.
- The HTTP status for business-rule failures other than the explicitly
  specified invalid-transition conflict.
- Database entities, field types, tables, relationships, indexes, and schema
  constraints.
- Detailed UI routes, components, layouts, interaction flow, and styling.
- The standalone representation and exhaustive test cases for the state
  machine.
- Detailed test tooling, suite organization, fixtures, and coverage targets.
- Operational database deployment, backup, and recovery arrangements.

These items must be resolved in the appropriate later specification without
changing the approved requirements.

## 15. Explicit Non-Goals

This architecture does not introduce:

- Authentication.
- Authorization.
- Roles.
- Notifications.
- Attachments.
- Audit history.
- Pagination.
- Sorting.
- Ticket deletion.
- Comment editing or deletion.

These capabilities remain outside the approved scope unless the requirements
baseline is explicitly changed.
