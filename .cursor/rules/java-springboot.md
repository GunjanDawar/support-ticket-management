# Java Spring Boot Engineering Guidelines

## Purpose

These guidelines define the standards that AI-assisted development must follow
when working on the Support Ticket Management System.

## Java Version

- Use Java 21.
- Prefer modern Java features when they improve readability.
- Avoid unnecessary use of advanced language features.

## Code Quality

- Follow standard Java naming conventions.
- Prefer small, focused methods.
- Avoid duplicated business logic.
- Prefer composition over unnecessary inheritance.
- Keep classes focused on a single responsibility.
- Do not introduce abstractions without a clear reason.

## Spring Boot Architecture

Use a layered architecture:

Controller
    ↓
Service
    ↓
Repository
    ↓
Database

### Controller

Controllers should:

- Handle HTTP requests and responses.
- Validate request DTOs.
- Delegate business logic to services.
- Avoid containing business rules.

### Service

Services should:

- Contain business logic.
- Enforce domain/business rules.
- Coordinate repositories.
- Handle transactional operations where required.

### Repository

Repositories should:

- Handle persistence operations.
- Avoid business logic.

## Dependency Injection

- Use constructor injection.
- Do not use field injection.

## DTOs

- Do not expose persistence entities directly through REST APIs.
- Use request and response DTOs where appropriate.
- API contracts should remain independent of persistence models.

## Validation

- Use Jakarta Bean Validation for request validation.
- Use @Valid / @Validated where appropriate.
- Business validation must remain in the service/domain layer.

## Error Handling

- Use centralized exception handling.
- Prefer @RestControllerAdvice.
- Return consistent API error responses.
- Never expose stack traces or internal implementation details.

## Persistence

- Use JPA/Hibernate where appropriate.
- Use transactions deliberately.
- Avoid N+1 query problems.
- Do not load unnecessary data.

## Security

- Never hard-code passwords, API keys, tokens, or credentials.
- Never commit secrets.
- Use environment variables or configuration injection.

## AI Behaviour

Before implementing code:

1. Read the relevant specification.
2. Identify applicable project rules.
3. Explain assumptions when requirements are ambiguous.
4. Do not modify unrelated files.
5. Do not invent requirements.
6. Do not remove existing tests simply to make tests pass.
7. Prefer the simplest maintainable implementation.