# REST API Standards

## General

APIs should follow REST principles and use resource-oriented URLs.

## Ticket APIs

POST   /api/tickets
GET    /api/tickets
GET    /api/tickets/{id}
PATCH  /api/tickets/{id}

## Comment APIs

POST   /api/tickets/{id}/comments

## Status

PATCH  /api/tickets/{id}/status

## Search and Filtering

GET /api/tickets?keyword={keyword}
GET /api/tickets?status={status}

Search and filtering may be combined.

Example:

GET /api/tickets?keyword=payment&status=OPEN

## HTTP Status Codes

Use appropriate HTTP status codes.

201 Created
- Successful ticket creation.

200 OK
- Successful retrieval/update.

400 Bad Request
- Invalid request or business-rule violation.

404 Not Found
- Requested resource does not exist.

500 Internal Server Error
- Unexpected server-side failure.

## Error Response

Errors should use a consistent structure.

Example:

{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "Ticket cannot transition from CLOSED to OPEN",
  "path": "/api/tickets/1/status"
}

Do not expose stack traces or internal implementation details.

## API Design

- Do not expose database entities directly.
- Use DTOs.
- Validate request payloads.
- Keep API contracts documented in spec/api-contract.md.