# Development Standards

## Architecture

E-Bog'cha uses a modular monolith. Business modules remain isolated inside one deployable backend until measured operational needs justify extraction.

Backend modules must separate:

- API/controller layer
- application/use-case layer
- domain layer
- infrastructure/persistence layer

Frontend features must be grouped by business capability rather than by generic file type alone.

## Backend standards

- Java 21
- Spring Boot 3.x
- Maven wrapper committed to the repository
- Spring Security with JWT-based authentication
- Flyway for database migrations
- Bean Validation for input validation
- OpenAPI for API documentation
- Testcontainers for integration tests

Rules:

- Controllers contain no business logic.
- Entities are not exposed directly through APIs.
- DTO mapping is explicit.
- Transactions are defined in application services.
- Authorization is enforced server-side.
- Global exception handling returns a stable error contract.
- Logs must not expose passwords, tokens, passport data, PINFL, or sensitive child information.

## Frontend standards

- React with TypeScript
- Vite
- Feature-based structure
- Shared design tokens for Oxu Kids branding
- Typed API client
- Route and component authorization are UX controls only; backend authorization remains authoritative

Rules:

- Avoid `any` unless justified.
- Server state and local UI state are separated.
- Forms provide accessible labels and validation feedback.
- API errors are normalized centrally.
- No secrets are stored in frontend environment variables.

## Testing

Each feature should include the appropriate combination of:

- unit tests for business rules;
- repository/integration tests for persistence;
- API tests for contracts, validation, and authorization;
- frontend component tests for critical behavior;
- end-to-end tests for core business flows.

Critical flow for Version 1:

```text
Lead -> Tour -> Contract -> Payment -> Enrollment
```

## Definition of Done

A task is complete only when:

- acceptance criteria pass;
- code follows module boundaries;
- validation and authorization are implemented;
- tests pass;
- migrations and API changes are documented;
- no secrets or personal data are committed;
- relevant documentation is updated;
- the pull request has been reviewed.
