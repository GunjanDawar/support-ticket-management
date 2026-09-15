# Testing Guidelines

## General

Testing is mandatory for every business capability.

Tests must verify behaviour rather than implementation details.

## Test Types

Use an appropriate combination of:

- Unit tests
- Repository/data tests where useful
- Controller/API tests
- Integration tests
- End-to-end tests where appropriate

## Business Rules

Every important business rule must have:

- Positive tests
- Negative tests
- Boundary/validation tests where applicable

Tests must be traceable to the requirements and acceptance criteria they verify.

## State Machines

For each state machine defined by the project specification, tests must cover:

- Every valid transition defined by the specification.
- Every invalid transition defined by the specification.
- Relevant positive, negative, and boundary conditions.
- Persistence of state after accepted and rejected transitions.

## Integration Testing

Important workflows should be tested against the application/database
rather than only testing isolated methods.

State-machine integration tests must verify:

1. Initial state.
2. Requested transition.
3. HTTP/API result.
4. Persisted database state.

For rejected transitions:

1. Request invalid transition.
2. Verify appropriate error response.
3. Verify database state did not change.

## Test Quality

- Tests must be deterministic.
- Avoid unnecessary mocking.
- Do not modify production behaviour solely to satisfy a test.
- Do not delete failing tests without understanding the cause.
- Tests should clearly communicate the expected behaviour.