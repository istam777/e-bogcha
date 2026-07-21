# CRM Database Decisions

Status: Proposed authoritative decisions for DB-001C-02 documentation review
Decision ID: DB-001C-02A-DECISIONS

## 1. Sources and precedence

1. `database/design/e_bogcha_v2_approved.dbml` is authoritative for logical schema structure.
2. `docs/03_Database/DB-001A_architecture_decisions.md` is authoritative for previously approved implementation semantics.
3. This document records CRM-specific implementation decisions and the explicit self-duplicate architecture amendment.
4. `CRM_WORKFLOW_SPECIFICATION.md` defines application workflow behavior.
5. `CRM_REFERENCE_DATA_PROPOSAL.md` is authoritative for the approved V12 global reference tuple matrix.

No decision in this document creates Admissions, Finance, ERP, or other out-of-scope objects.

## 2. Implementation boundary

DB-001C-02 creates exactly 30 CRM and telephony tables. `lead_conversions` is deferred because its required `children` and `admission_applications` FK targets are Admissions-owned and absent from V1-V6.

The later CRM/Admissions boundary migration remains unversioned. It must be ordered after both target tables exist. Placeholder tables and incomplete foreign keys are prohibited.

## 3. Approved database constraints

### 3.1 Active lead assignment

Approved deterministic index name and definition:

```sql
CREATE UNIQUE INDEX ux_lead_assignments_active_lead
    ON lead_assignments (lead_id)
    WHERE ended_at IS NULL;
```

This enforces at most one active assignment per lead while permitting assignment history. It does not synchronize `leads.owner_user_id`; synchronization belongs to the application transaction.

### 3.2 Primary lead phone

Approved deterministic index name and definition:

```sql
CREATE UNIQUE INDEX ux_lead_phones_primary_lead
    ON lead_phones (lead_id)
    WHERE is_primary;
```

This enforces at most one primary phone. Zero primary phones and multiple non-primary phones remain database-valid.

### 3.3 Self-duplicate prevention

The following is an explicit CRM architecture amendment to the approved DBML implementation:

```sql
CONSTRAINT ck_lead_duplicates_not_self
    CHECK (lead_id <> duplicate_of_lead_id)
```

The DBML unique `(lead_id, duplicate_of_lead_id)` remains. Reverse-pair and cross-organization rules remain application-enforced; no trigger or canonical-pair expression index is approved.

### 3.4 Existing DBML and DB-001A requirements

- Every FK uses explicit `ON DELETE RESTRICT ON UPDATE RESTRICT`.
- Every FK has leading valid B-tree index coverage.
- `(pbx_config_id, external_call_id)` is unique on `call_sessions`.
- `(pbx_config_id, external_event_id)` is unique on `webhook_events`.
- `call_events.external_event_id` is nullable, indexed, and non-unique.
- DBML-declared scoped code and telephony uniqueness is preserved.
- Business UUIDs have no PostgreSQL generation default; application services generate UUIDv4 values.
- No PostgreSQL extension is required by DB-001C-02.
- No CRM object uses `UNIQUE NULLS NOT DISTINCT`.

## 4. Explicitly application-enforced decisions

No database trigger or composite-FK redesign is approved for:

- active-assignment and `leads.owner_user_id` synchronization;
- assignment transfer atomicity;
- at least one phone and exactly one primary before an application transaction completes;
- E.164 phone normalization and organization-scoped duplicate lookup;
- reverse duplicate-pair and cross-organization duplicate rejection;
- status/history atomicity and history mutation permissions;
- lost/archive field consistency and reopening;
- 24-hour initial-contact SLA calculation;
- tour authorization, rescheduling activity, attendance, and outcome consistency;
- branch-access and organization-boundary consistency;
- telephony duration and timestamp ordering;
- recording authorization, signed URL issuance, sanitization, and retention; and
- secret and phone-number logging controls.

Service-level negative integration tests must prove rejection of each invalid cross-organization, cross-branch, authorization, temporal, and workflow scenario.

## 5. Migration boundaries

| Migration | Tables | Count |
|---|---|---:|
| `V7__create_crm_reference_schema.sql` | `lead_sources`, `lead_statuses`, `lost_reasons`, `tour_outcomes`, `lead_task_statuses`, `lead_activity_types` | 6 |
| `V8__create_crm_lead_core_schema.sql` | `leads`, `lead_phones`, `prospective_children` | 3 |
| `V9__create_crm_workflow_schema.sql` | `lead_assignments`, `lead_status_history`, `lead_activities`, `lead_notes`, `lead_tasks`, `tours`, `lead_duplicates` | 7 |
| `V10__create_telephony_configuration_schema.sql` | `call_directions`, `call_dispositions`, `call_event_types`, `webhook_statuses`, `pbx_configs`, `extensions`, `sip_accounts`, `phone_numbers` | 8 |
| `V11__create_telephony_call_schema.sql` | `call_sessions`, `call_participants`, `call_events`, `call_recordings`, `lead_calls`, `webhook_events` | 6 |

Total: 30 tables. `V12` is reserved for explicitly approved global CRM and telephony reference seeds. Organization-owned rows are excluded. `lead_conversions` remains unversioned.

## 6. Database acceptance tests for V7-V11

The database integration suite must preserve the existing 22 tests and verify:

- clean Flyway application from V1 through V11;
- exactly 54 application tables after V11;
- exact metadata for every new column;
- exact cumulative PK, UNIQUE, FK, and CHECK counts;
- explicit `RESTRICT/RESTRICT` on every FK;
- leading valid B-tree coverage for every FK;
- exact approved DBML index inventory plus the two partial unique indexes;
- no PostgreSQL UUID defaults;
- no unapproved `NULLS NOT DISTINCT` objects;
- rejection of a second active lead assignment;
- historical assignments coexisting with one active assignment;
- rejection of a second primary phone;
- acceptance of zero primary and multiple non-primary phones;
- acceptance of the same normalized phone across leads;
- rejection of duplicate directed and self-duplicate lead pairs;
- PBX-scoped extension, SIP username, phone number, external call ID, and external webhook ID uniqueness;
- the same call or webhook external ID being accepted across PBX configurations;
- nullable, repeatable `call_events.external_event_id`;
- rejection of duplicate `lead_calls` pairs;
- empty reference tables before V12;
- absence of Admissions, Finance, attendance, medical, kitchen, warehouse, and payroll tables;
- absence of JPA/Hibernate schema generation; and
- successful Spring context startup.

With the approved self-duplicate check, the expected V7-V11 CRM additions are 30 PK constraints, 16 ordinary unique constraints, 61 FKs, and one CHECK constraint. The two partial unique indexes are PostgreSQL indexes rather than `pg_constraint` unique constraints. Combined with V1-V6, the expected cumulative baseline is 54 PKs, 37 ordinary unique constraints, 98 FKs, and one CHECK constraint.

## 7. Future service-level tests

Service tests, not schema tests, must cover:

- cross-organization and cross-branch rejection;
- status/lost/archive consistency and reopening;
- reverse duplicate links;
- at-least-one-phone and exactly-one-primary completion rules;
- UZ/E.164 normalization and invalid-number rejection;
- assignment/current-owner synchronization and atomic transfer;
- SLA timestamp behavior;
- tour rescheduling and timeline activity;
- call/recording temporal validation; and
- recording authorization, audit, masking, and retention behavior.

## 8. Non-decisions

DB-001C-02 does not approve:

- a status transition graph in SQL;
- status-history mutation triggers;
- an active-tour unique index;
- a tour status or reschedule-history table;
- phone-number uniqueness;
- participant-role reference data;
- telephony duration or timestamp CHECK constraints;
- recording presence or cleanup constraints;
- cross-table consistency triggers;
- cascading FKs; or
- a Flyway version for `lead_conversions`.

## 9. Global reference seed identity and replay policy

Decision ID: DB-001C-02B-06A
Status: Approved

### Decision

- The 28 tuples in `CRM_REFERENCE_DATA_PROPOSAL.md` are approved exactly as documented.
- V12 is authorized as a data-only Flyway versioned migration.
- V12 uses the approved fixed UUIDs; each UUID/code pair is an immutable release identity.
- V12 uses strict plain `INSERT` statements.
- `ON CONFLICT`, `MERGE`, conditional inserts, delete-then-insert behavior, and updates of existing reference identities are prohibited.
- An existing conflicting UUID or globally unique code causes visible migration failure.
- V12 does not update, delete, ignore, merge, or replace existing rows.
- Later display-name, flag, or identity changes require a new explicitly approved versioned migration.
- Organization-owned reference data remains unseeded.

### Rationale

- Deterministic reference identities produce consistent environments.
- Visible migration failures expose schema and reference-data drift.
- Strict inserts prevent silent identity corruption.
- Stable UUIDs provide durable foreign-key identities.
- Later versioned migrations provide an auditable evolution history through Flyway.

### Consequences

- Clean installations receive exactly the 28 approved global tuples.
- Environments containing conflicting identities fail while applying V12.
- Operators must resolve unexpected drift rather than bypassing it.
- Display-name or approved-flag changes require later versioned migrations.
