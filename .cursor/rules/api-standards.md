# REST API Standards

## General

APIs should follow REST principles and use resource-oriented URLs.

Project-specific endpoints, request and response contracts, and status-code
decisions belong in the project's API specification.

## HTTP Methods

- Use HTTP methods according to their standard semantics.
- Use POST to create resources or invoke non-idempotent operations.
- Use GET to retrieve resources without changing server state.
- Use PUT for complete replacement where that behavior is specified.
- Use PATCH for explicitly defined partial updates.
- Use DELETE to remove resources where deletion is specified.

## Resource Updates and State Transitions

When the API specification defines a dedicated status-transition operation,
ordinary resource updates must not implicitly bypass the specified
state-transition rules. The API specification must define which fields each
update operation accepts and the behavior of each transition operation.

## HTTP Status Codes

Use HTTP status codes consistently with their standard semantics and the
project's API specification.

The API specification must define status codes for successful operations,
request-validation failures, business-rule violations, missing resources,
and unexpected server failures. Do not assume that validation failures and
business-rule violations necessarily use the same status code.

## Error Response

Errors must use a consistent structured response defined by the API
specification. The structure should include:

- A stable, machine-readable error code.
- A human-readable message.
- The HTTP status.
- Request context such as the request path where useful.
- Field-level validation details when applicable.

Do not expose stack traces or internal implementation details.

## API Design

- Do not expose database entities directly.
- Use DTOs.
- Validate request payloads.
- Keep project-specific API contracts documented in the project's API
  specification.