# Generate Tests

Review the relevant specification and implementation.

Identify behaviour that requires testing.

Generate tests for:

- Happy paths.
- Validation failures.
- Business-rule violations.
- Error scenarios.
- Boundary conditions.
- Persistence behaviour where appropriate.

For state-machine functionality:

1. Enumerate every valid transition.
2. Enumerate invalid transitions.
3. Generate tests for both.
4. Verify persisted state after successful transitions.
5. Verify state remains unchanged after rejected transitions.

Do not change production code unless explicitly requested.

Prefer meaningful tests over high test-count.