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

## Ticket State Machine

The ticket state machine is a critical business rule.

Tests must cover all explicitly valid transitions:

OPEN → IN_PROGRESS
IN_PROGRESS → RESOLVED
RESOLVED → CLOSED
OPEN → CANCELLED
IN_PROGRESS → CANCELLED

Tests must also verify invalid transitions.

Examples include:

CLOSED → OPEN
RESOLVED → OPEN
CANCELLED → OPEN
OPEN → RESOLVED
OPEN → CLOSED
IN_PROGRESS → CLOSED

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