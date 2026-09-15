# AI Review Findings

## Scope

This document records the findings from the Milestone 1 review of the
Spec-Driven Development foundation. It does not define project requirements.

## Confirmed Findings

- Reusable testing rules contained project-specific ticket state transitions.
  This mixed domain requirements with engineering guidance and risked making
  the rules a competing source of truth. The transitions were removed; the
  testing rules now require coverage of transitions defined by the project
  specification.
- Reusable API standards contained project-specific ticket, comment, status,
  search, and filtering endpoints. These were removed because endpoint
  contracts belong in the project API specification.
- The relationship between an ordinary resource PATCH and a dedicated
  status-transition operation was unclear. The API standards now state that
  ordinary updates must not bypass specified state-transition rules.
- Error guidance grouped request validation and business-rule failures under
  one predetermined status code and lacked a stable machine-readable error
  code and field-level validation details. The guidance now distinguishes
  error categories without deciding project-specific status codes.
- Testing guidance did not explicitly require traceability from requirements
  through acceptance criteria to tests. That requirement has been added.

## Optional Recommendations Not Adopted

The review also suggested guidance for authentication, authorization,
pagination, sorting, concurrent updates, input limits, content types,
identifier formats, and creation response headers. These recommendations were
not adopted because they could introduce design decisions or features that
have not been established by the project specification.

Suggestions concerning specific testing frameworks, database tooling, and
non-functional requirements were also not added. Those choices should follow
the approved specification and project context rather than being imposed by
generic rules.

## Decisions Deferred to the Specification Phase

The specification phase must decide, where applicable:

- The project-specific resources, endpoint paths, and request and response
  contracts.
- Whether a resource exposes ordinary partial updates, dedicated
  state-transition operations, or both, and which fields each operation may
  change.
- The valid and invalid state transitions for each specified state machine.
- The HTTP status code for each success and error scenario, including request
  validation, business-rule violations, missing resources, and unexpected
  failures.
- The exact structured error schema, including stable error codes and
  field-level validation details.
- The canonical location and organization of project specifications.
