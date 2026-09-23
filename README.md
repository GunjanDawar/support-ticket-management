# Support Ticket Management System

A Support Ticket Management System developed using **Spec-Driven Development (SDD)** and AI-assisted engineering.

## Technology Stack

* Java 21
* Spring Boot
* PostgreSQL / H2
* REST API
* React / Vite
* Cursor
* GitHub Copilot

## Development Approach

The project follows a specification-first development workflow:

Requirement
↓
Specification
↓
Plan / Tasks
↓
Implementation
↓
Testing
↓
Review
↓
Fix
↓
Final SDD Audit

## Specification

The approved specifications are maintained under `spec/`:

* `requirements.md` — Functional, business, validation, error, persistence, and acceptance requirements
* `architecture.md` — System architecture and layer responsibilities
* `data-model.md` — Persistent entities, relationships, constraints, and indexing
* `api-contract.md` — REST endpoints, request/response contracts, and error handling
* `state-machine.md` — Ticket lifecycle and valid/invalid transitions
* `ui-flow.md` — Frontend flows, actions, validation, and error behavior
* `test-strategy.md` — Test levels, coverage, and verification strategy
* `implementation-plan.md` — Incremental implementation tasks and Definition of Done

## Ticket Lifecycle

The system supports the following ticket statuses:

* `OPEN`
* `IN_PROGRESS`
* `RESOLVED`
* `CLOSED`
* `CANCELLED`

Allowed transitions:

* `OPEN → IN_PROGRESS`
* `IN_PROGRESS → RESOLVED`
* `RESOLVED → CLOSED`
* `OPEN → CANCELLED`
* `IN_PROGRESS → CANCELLED`

`CLOSED` and `CANCELLED` are terminal states.

The backend service layer is authoritative for lifecycle and terminal-state rules.

## Features

* Create, list, and view support tickets
* Update title, description, priority, and assignee
* Add comments
* Search tickets by title or description
* Filter tickets by status
* Combine search and status filtering
* Controlled ticket status transitions
* Backend validation
* Structured API errors
* PostgreSQL persistence
* H2-based automated testing
* Frontend UI for ticket management

## Application Screenshots

* [Create Ticket](docs/screenshots/create-ticket.png) — Create a new support ticket with title, description, priority, and optional assignee.
* [Edit Ticket](docs/screenshots/edit-ticket.png) — Update the permitted ticket fields.
* [Ticket List](docs/screenshots/ticket-list.png) — View all support tickets with their current status, priority, and assignee.
* [Ticket Details](docs/screenshots/ticket-details.png) — View complete ticket details and associated comments.
* [Search Ticket](docs/screenshots/search-ticket.png) — Search tickets by keyword across title and description.
* [Terminal Ticket](docs/screenshots/terminal-ticket.png) — Demonstrate restrictions applied to `CLOSED` and `CANCELLED` tickets.
* [PostgreSQL Ticket Table](docs/screenshots/local-db-ticket-table.png) — Verify persisted ticket data in the PostgreSQL database.
* [PostgreSQL Comment Table](docs/screenshots/local-db-comment-table.png) — Verify persisted comment data and ticket association in PostgreSQL.


## Testing & Verification

The completed implementation was verified through automated tests and runtime checks.

* Backend: **220 tests passing**
* Frontend: **43 tests passing**
* Frontend production build: **successful**
* PostgreSQL persistence: **verified**
* REST API behavior: **verified**
* Complete 25-case state-transition matrix: **verified**
* Terminal-state restrictions: **verified**
* Application restart durability: **verified**

## AI-Assisted Engineering

AI tools were used as engineering assistants within the SDD workflow.

* **Cursor** — specification-driven implementation, task execution, testing, and review
* **GitHub Copilot** — AI-assisted development support

AI-generated implementation was reviewed against the approved specifications and verified through automated tests, REST testing, database verification, and final SDD review.

## Scope

The implementation intentionally remains within the approved requirements.

Features such as authentication, authorization, users/roles, notifications, attachments, audit history, ticket deletion, pagination, sorting, ticket reopening, additional states/transitions, and external search infrastructure are outside the approved scope.

## Project Status

**Implementation and verification completed.**

The project has completed the planned implementation and testing stages, including PostgreSQL persistence and restart-durability verification.

A final SDD audit was performed to check consistency between the approved requirements, specifications, implementation, and verification evidence.
