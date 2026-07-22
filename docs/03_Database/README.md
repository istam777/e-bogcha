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

### Database foundation delivery status

Status: **COMPLETE**

- Merged PR: #2
- Merge method: merge commit
- Merge commit: `90dbe5f3f4e8700d20bbd8f7454c058a19d758dc`
- Merged at: `2026-07-22T10:33:29Z`
- Merged by: `istam777`
- Feature head: `92b3a9abd4eb93762c8efdae473c4529e748d5e7`
- Migrations: V1-V13
- Review threads: 1 resolved, 0 unresolved
- Post-merge validation: 75/75 passed on PostgreSQL 17.10
- Local and remote `main`: synchronized
- Final documentation alignment: documentation-only change prepared on `docs/database-foundation-completion`
- Documentation delivery lifecycle: tracked in Git and GitHub rather than maintained as mutable current-state text

See [CORE_REFERENCE_DATA_PROPOSAL.md](CORE_REFERENCE_DATA_PROPOSAL.md) for the
detailed V13 tuple contract, review-remediation evidence, merge record, and
post-merge validation evidence. COMPLETE applies only to the database-foundation
delivery; it does not declare the complete E-Bog'cha product finished.

Detailed table definitions and ER diagrams will be added incrementally with each module before implementation.
