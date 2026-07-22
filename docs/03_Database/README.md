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
tuple matrix remains fixed for V13.

### V13 delivery status

V13 is implemented and DB-001C-05D = APPROVED. Implementation commit
`aaaee9c92600d3c36de37d825c778114e16d9e3c` was pushed to
`origin/feature/database-foundation`; the remote branch and PR #2 head both
match that commit. Full validation passed 75/75 tests with 0 failures, 0 errors,
and 0 skipped.

GitHub checks are not configured. Review thread `PRRT_kwDOTcawyc6S0eZR`
remains unresolved, the PR description update is pending, and merge readiness
has not yet been declared. Final review and the merge decision remain pending
dedicated workflows.

Detailed table definitions and ER diagrams will be added incrementally with each module before implementation.
