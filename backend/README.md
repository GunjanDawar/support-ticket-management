# Backend

Java 21 Spring Boot foundation for the Support Ticket Management System.

## Commands

- Build: `./gradlew build`
- Test: `./gradlew test`
- Run locally: `./gradlew bootRun --args='--spring.profiles.active=local'`

## Database configuration

Application and local runtime use PostgreSQL. Supply credentials externally:

- `DB_URL` — PostgreSQL JDBC URL. The local profile defaults to
  `jdbc:postgresql://localhost:5432/support_ticket_management`.
- `DB_USERNAME` — PostgreSQL username.
- `DB_PASSWORD` — PostgreSQL password.

Copy `.env.example` to a local ignored environment file if useful for local
tooling, replace its placeholders, and load those values into the process
environment before startup. Spring Boot does not load `.env` files itself.

Automated tests activate the `test` profile and use an isolated in-memory H2
database. H2 is not an application-runtime fallback.

Schema migrations and application entities are intentionally deferred to later
implementation tasks.
