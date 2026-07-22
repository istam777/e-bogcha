# Database Architecture

## Platform

- Database: PostgreSQL 17
- Identifier strategy: UUID
- Naming: plural `snake_case` table names and `snake_case` columns
- Schema evolution: versioned migrations only
- ORM must not modify production schema automatically

## Global conventions

Business tables should normally include:

```text
id UUID PRIMARY KEY
created_at TIMESTAMPTZ NOT NULL
created_by UUID NULL
updated_at TIMESTAMPTZ NOT NULL
updated_by UUID NULL
deleted_at TIMESTAMPTZ NULL
version BIGINT NOT NULL
```

`deleted_at` is used only where soft deletion is required. Immutable financial and audit records must not be silently soft-deleted.

## Data domains

### Core

- branches
- users
- roles
- permissions
- user_roles
- role_permissions
- settings
- files
- audit_logs
- notifications

### CRM

- lead_sources
- lead_statuses
- leads
- lead_assignments
- lead_comments
- lead_activities
- tours

### Admissions

- families
- parents
- children
- contracts
- contract_templates
- student_documents
- document_types

### Finance foundation

- tariffs
- discounts
- contract_discounts
- payment_plans
- payments
- payment_extensions
- refund_requests

### ERP foundation

- academic_years
- groups
- students
- student_enrollments

### Telephony

- pbx_connections
- sip_accounts
- call_sessions
- call_events
- call_recordings

## Required rules

- Foreign keys are explicit and indexed where they are used for joins or filters.
- Phone numbers are normalized before duplicate checks.
- Contract numbers are unique and generated centrally.
- Lead statuses, sources, tariffs, discounts, languages, nationalities, relationship types, admission statuses, and document types are represented as configurable data rather than database enums.
- Monetary values use `NUMERIC`, never floating-point types.
- Dates and timestamps have explicit semantics; stored timestamps use `TIMESTAMPTZ`.
- Real personal data must never be included in migrations, seed files, fixtures, or tests.

## Migration policy

- Flyway will own schema migrations.
- Applied migrations are immutable.
- Every destructive change requires a documented rollout and rollback strategy.
- Seed data is separated from structural migrations when practical.

## Core reference seed proposal

DB-001A requires minimum system-owned core seed identities. The approved
[CORE_REFERENCE_DATA_PROPOSAL.md](CORE_REFERENCE_DATA_PROPOSAL.md) contains a
complete 16-row tuple matrix across five tables. Status: APPROVED. The exact
tuple matrix is fixed for future V13 implementation.

V13 has not yet been created. PR #2 remains not ready to merge until V13 is
implemented, integration tests are updated, validation passes, the implementation
is committed and pushed, the PR description is updated, the existing review
thread is remediated, and the implementation passes final PR re-review.

Detailed table definitions and ER diagrams will be added incrementally with each module before implementation.
