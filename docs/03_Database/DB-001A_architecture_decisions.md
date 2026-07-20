# DB-001A.1 - Database Architecture Decisions

Status: Approved
Project: E-Bog'cha V1
Company: Oxu Kids
Decision scope: Resolution of DB-001A audit blockers and implementation boundaries

## 1. Purpose

This document records the approved database architecture decisions that resolve the blockers and high-severity findings identified by DB-001A. It is the implementation contract for the database infrastructure bootstrap in DB-001B and the later business-schema work in DB-001C.

The decisions in this document are normative. Implementations must not weaken, reinterpret, or extend them without a separately approved architecture decision.

## 2. Scope

This document defines:

- enrollment lifecycle and student activation integrity;
- phone, telephony, nullable-scope, timestamp, UUID, and seed policies;
- financial and group-capacity concurrency protection;
- invoice due-date extension behavior;
- foreign-key and audit-log policies;
- PII encryption and equality-lookup protection;
- the boundary between DB-001B and DB-001C.

This document does not create or revise the logical schema, assign organization identifiers, assign fixed UUID values to seed rows, or authorize business-schema migrations.

## 3. Authoritative artifacts

The authoritative logical database design is:

`database/design/e_bogcha_v2_approved.dbml`

The approved DBML remains authoritative for tables, columns, relationships, nullability, and timestamp presence. This decision document is authoritative for the implementation semantics explicitly resolved here.

If this document and the approved DBML appear technically inconsistent during implementation, work must stop and the conflict must be reported. Neither artifact may be silently rewritten or reinterpreted.

## 4. Enrollment and student activation

### Enrollment lifecycle

The system-owned enrollment status codes are:

- `PLANNED`
- `ACTIVE`
- `COMPLETED`
- `CANCELLED`

A current active enrollment has status code `ACTIVE` and `end_date IS NULL`. A student may have no more than one current active enrollment.

`PLANNED` enrollments are not active and do not consume group capacity. An implementation based only on `end_date IS NULL` is invalid because a planned enrollment may also have no end date.

Enforcement requires both:

- a transactional application service; and
- hard PostgreSQL concurrency protection using a stable system-owned `ACTIVE` enrollment status identifier or an equally strong implementation that does not depend only on application validation.

### Student activation

A student may enter ERP and receive an `ACTIVE` enrollment only when all of the following are satisfied in the same transaction:

- the contract belongs to the same child and admission application;
- the contract status is `SIGNED` or `ACTIVE`;
- the contract has at least one non-cancelled invoice;
- at least one invoice linked to the contract is fully paid;
- only `CONFIRMED` payments count toward payment;
- payment allocations, reduced by applicable refunds, cover the invoice total;
- an administrator selected the branch and group;
- the group has available capacity; and
- the student, enrollment, contract, admission application, and child references are consistent.

During successful activation:

- the student becomes `ACTIVE`;
- the enrollment becomes `ACTIVE`;
- the contract may transition from `SIGNED` to `ACTIVE`; and
- every operation succeeds or the entire transaction rolls back.

The application service is the primary workflow coordinator. The database must provide hard guards against duplicate active enrollment, group over-capacity, invalid cross-entity references, and activation without the required contract, payment, and group conditions.

## 5. Phone integrity

For `lead_phones` and `guardian_phones`, the database permits at most one primary phone per owner. PostgreSQL must enforce this with partial unique indexes scoped to the owning lead or guardian where `is_primary` is true.

Zero primary phones may temporarily exist during import or draft editing. Application validation must require exactly one primary phone before a lead or admission record becomes operational.

DB-001 does not authorize a deferred database trigger whose sole purpose is to require the existence of a primary phone.

## 6. Telephony idempotency

`call_sessions.external_call_id` is unique within one PBX configuration. The required uniqueness scope is `(pbx_config_id, external_call_id)`.

`webhook_events.external_event_id` is required and unique within one PBX configuration. The required uniqueness scope is `(pbx_config_id, external_event_id)`, and it is the database key for webhook idempotency.

`call_events.external_event_id` may be null, remains indexed, and is not unique. Webhook-level idempotency belongs to `webhook_events`; call-level idempotency belongs to `call_sessions`.

## 7. Nullable-scope uniqueness

A null branch represents one organization-level namespace for organization/branch-scoped objects.

`departments` must use PostgreSQL uniqueness equivalent to:

```sql
UNIQUE NULLS NOT DISTINCT (organization_id, branch_id, code)
```

The same null-as-one-scope behavior applies where explicitly required by the approved design for:

- `application_settings`;
- `number_sequences`; and
- `tariffs`.

`NULLS NOT DISTINCT` must not be applied automatically to unrelated nullable personal-data fields.

## 8. Timestamp policy

The approved DBML timestamp definitions are intentional and authoritative.

- Mutable business entities normally contain `created_at` and `updated_at`.
- Immutable history and event records normally contain only a creation or event time.
- Pure junction tables may omit `updated_at`.
- Static lookup and status tables may omit both timestamps.
- Existing timestamp omissions in the approved DBML are accepted unless a future approved schema revision changes them.

The generic requirement to add timestamps where business-appropriate does not authorize adding `created_at` or `updated_at` beyond the approved DBML.

## 9. UUID policy

Java application services generate business-entity UUIDs consistently as UUID version 4 values. Database table identifiers must not depend on PostgreSQL UUID default-generation functions.

System-owned reference rows use fixed UUID constants committed in seed migrations. After release:

- the UUID is immutable;
- the code is immutable; and
- display name, sort order, and active flags may change where the schema allows.

No PostgreSQL UUID extension is required solely for identifier generation.

## 10. Seed ownership model

Reference data has two ownership categories:

1. System-owned global reference data uses fixed UUID constants and immutable codes in versioned seed migrations.
2. Organization-scoped bootstrap data is created only after the organization bootstrap strategy and organization identifiers are approved.

Seed migrations must not contain real personal data, runtime secrets, environment-specific credentials, or organization-scoped rows whose owning identifiers have not been approved.

Exact fixed UUID constants are intentionally not assigned by this decision document. They must be defined explicitly in the approved DB-001C seed implementation and then remain immutable after release.

## 11. Approved system-owned seed codes

The minimum system-owned seed codes are:

| Table | Codes |
|---|---|
| `user_statuses` | `ACTIVE`, `INACTIVE`, `LOCKED`, `SUSPENDED` |
| `languages` | `UZ`, `RU` |
| `gender_types` | `MALE`, `FEMALE` |
| `relationship_types` | `FATHER`, `MOTHER`, `GUARDIAN` |
| `document_types` | `BIRTH_CERTIFICATE`, `PASSPORT`, `ID_CARD`, `PINFL`, `MEDICAL_CERTIFICATE`, `PHOTO` |
| `document_verification_statuses` | `PENDING`, `VERIFIED`, `REJECTED` |
| `lead_task_statuses` | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `lead_activity_types` | `CALL`, `NOTE`, `STATUS_CHANGE`, `ASSIGNMENT`, `TOUR`, `SYSTEM` |
| `invoice_statuses` | `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `CANCELLED` |
| `payment_statuses` | `PENDING`, `CONFIRMED`, `FAILED`, `CANCELLED` |
| `payment_methods` | `CASH`, `CARD`, `BANK_TRANSFER`, `ONLINE` |
| `student_statuses` | `ACTIVE`, `INACTIVE`, `WITHDRAWN`, `GRADUATED` |
| `enrollment_statuses` | `PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED` |
| `call_directions` | `INBOUND`, `OUTBOUND` |
| `call_dispositions` | `ANSWERED`, `MISSED`, `BUSY`, `REJECTED`, `FAILED`, `NO_ANSWER` |
| `call_event_types` | `STARTED`, `RINGING`, `ANSWERED`, `ENDED`, `RECORDING_AVAILABLE` |
| `webhook_statuses` | `RECEIVED`, `PROCESSING`, `PROCESSED`, `FAILED`, `IGNORED` |

## 12. Organization bootstrap data

The following values are organization-scoped bootstrap data rather than global reference rows:

| Table | Codes |
|---|---|
| `lead_sources` | `SOCIAL_MEDIA`, `PHONE`, `WALK_IN` |
| `lead_statuses` | `NEW`, `CONTACTED`, `TOUR_PLANNED`, `SUCCESSFUL`, `NO_SHOW`, `LOST`, `ARCHIVED` |
| `tour_outcomes` | `CONTRACT`, `THINKING`, `NO_SHOW` |
| `admission_statuses` | `IN_REVIEW`, `DRAFT_CONTRACT`, `PRINTED`, `SIGNED`, `ACTIVE`, `CANCELLED` |
| `contract_statuses` | `DRAFT`, `PRINTED`, `SIGNED`, `ACTIVE`, `TERMINATED`, `CANCELLED` |
| `discount_types` | `EMPLOYEE_CHILD`, `OXU_STUDENT_CHILD`, `SIBLING` |

Tariffs and tariff options are also organization-scoped bootstrap data. The approved initial tariff policy is:

- monthly amount: 2,100,000 UZS;
- 1 month: 0%;
- 3 months: 5%;
- 6 months: 10%; and
- 10 months: 15%.

No organization, branch, tariff, tariff option, or organization-scoped status may be seeded until the organization bootstrap strategy and owning identifiers are approved.

## 13. Financial concurrency and integrity

Transactional application services and hard database protection are both required for:

- preventing allocations from exceeding the confirmed usable payment amount;
- preventing allocations from exceeding the invoice outstanding balance;
- preventing refunds from exceeding the payment amount;
- contract price arithmetic; and
- invoice due-date extension consistency.

Applicable refunds reduce the usable payment balance.

Allocation and refund transactions must:

- lock affected payment rows;
- lock affected invoice rows;
- calculate aggregate totals inside the same transaction;
- acquire locks in deterministic order; and
- reject any result that would create an invalid balance.

Ordinary row-level `CHECK` constraints are insufficient for aggregate cross-row totals. Constraint triggers or equivalent hard PostgreSQL guards are approved.

## 14. Group-capacity protection

`groups.capacity` must satisfy:

```text
capacity BETWEEN 1 AND 25
```

Actual `ACTIVE` enrollment occupancy must not exceed group capacity. `PLANNED`, `COMPLETED`, and `CANCELLED` enrollments do not consume active capacity.

Enforcement requires transactional application validation, row locking, and database-level concurrency protection.

## 15. Due-date extension workflow

An invoice due-date extension must:

- reference an invoice;
- record the old due date and new due date;
- satisfy `new_due_date > old_due_date`;
- require `old_due_date` to equal the invoice due date immediately before the change;
- update the invoice and insert the history row in one transaction; and
- record approver, reason, and approval time.

The operation must be atomic and protected by both the transactional application service and a hard database consistency guard.

## 16. Foreign-key action policy

The default business foreign-key policy is:

- `ON DELETE RESTRICT`; and
- `ON UPDATE RESTRICT`.

`CASCADE` and `SET NULL` are prohibited unless a future approved decision explicitly authorizes an exception. Soft deletion, status transitions, and archival are preferred over deleting business records.

## 17. Audit immutability

`audit_logs` is append-only.

- `INSERT` is allowed.
- `UPDATE` is prohibited.
- `DELETE` is prohibited.

The future database implementation must include a hard database guard that prevents ordinary application operations from updating or deleting audit rows. Application behavior must enforce the same restriction.

## 18. PII encryption and lookup-hash policy

PINFL and identity-document values use application-level protection.

Encryption requirements:

- AES-256-GCM authenticated encryption;
- a ciphertext envelope containing the key version; and
- encryption keys never stored in the database or repository.

Equality-lookup requirements:

- HMAC-SHA-256;
- a separate lookup-HMAC key; and
- no raw unsalted hash for PINFL or identity-document values.

Stored representations are ciphertext, deterministic keyed lookup hash, and masked display value.

Secrets must come from runtime secret management. Local-development secrets may exist only in an untracked local environment file. Production must use an external secret manager, Vault, or a cloud KMS-equivalent system.

The following must never be logged:

- plaintext PINFL;
- plaintext passport or identity-document numbers;
- encryption keys;
- HMAC keys; or
- decrypted values.

## 19. Phase decomposition

### DB-001B - Infrastructure bootstrap

DB-001B is limited to:

- Java 21;
- Spring Boot;
- Gradle wrapper;
- PostgreSQL 17 Docker service;
- Flyway wiring;
- datasource configuration;
- an empty migration location; and
- database-connectivity and migration smoke tests.

DB-001B must not create any of the 88 business tables.

### DB-001C - Business schema

Business SQL migration implementation begins only in DB-001C after DB-001B infrastructure has been reviewed and approved. DB-001C must implement the approved DBML and the constraints established by this document without inventing additional rules.

## 20. Resolved DB-001A blockers

Every BLOCKER and HIGH finding from DB-001A is mapped below.

| Original severity and finding | Approved resolution | Status for DB-001B |
|---|---|---|
| BLOCKER - active enrollment and student activation semantics were undefined | Sections 4, 11, 13, and 14 define the status codes, active predicate, qualifying contract/payment conditions, atomic workflow, capacity behavior, and hard database guards. | Resolved |
| BLOCKER - `call_events.external_event_id` uniqueness conflicted with the requested idempotency invariant | Section 6 confirms that the field is nullable, indexed, and non-unique; idempotency belongs to `webhook_events` and `call_sessions`. | Resolved |
| BLOCKER - system reference seed codes and identifiers were unspecified | Sections 9-12 define seed ownership and all minimum codes. Fixed UUID values are intentionally assigned in DB-001C seed migrations and become immutable after release. | Resolved for infrastructure bootstrap |
| BLOCKER - timestamp convention conflicted with approved DBML omissions | Section 8 confirms that the DBML is authoritative and its omissions are intentional. | Resolved |
| HIGH - the approved DBML lacked a stable governance baseline | Section 3 explicitly names the DBML as authoritative and requires conflicts to stop implementation. Repository review/approval remains a governance step, not a reason to alter the artifact in this task. | Resolved for infrastructure bootstrap |
| HIGH - aggregate financial invariants were vulnerable to concurrent over-allocation and over-refund | Section 13 requires deterministic locking, same-transaction totals, application services, and hard PostgreSQL guards. | Resolved |
| HIGH - group occupancy and student activation were vulnerable to concurrent invalid activation | Sections 4 and 14 require row locking, database concurrency protection, cross-entity validation, and atomic activation. | Resolved |
| HIGH - nullable department branch scope could allow duplicate organization-level codes | Section 7 requires `UNIQUE NULLS NOT DISTINCT` semantics for departments. | Resolved |
| HIGH - encryption, key ownership, rotation envelope, and lookup hashing were undefined | Section 18 defines AES-256-GCM, key-version envelopes, external key custody, HMAC-SHA-256 lookup protection, masking, and logging restrictions. | Resolved |
| HIGH - backend, Flyway, Docker, and migration infrastructure did not exist | Section 19 makes that missing foundation the exact and exclusive scope of DB-001B. | Resolved by approved DB-001B scope |

## 21. Remaining non-blocking risks

- Exact fixed UUID constants for system-owned reference rows remain to be assigned in DB-001C. This does not affect the empty Flyway bootstrap in DB-001B.
- Organization and branch identifiers, bootstrap ownership, and organization-scoped seed execution remain unapproved. Those rows must not be seeded until approval is recorded.
- The exact PostgreSQL implementation form for aggregate financial guards, capacity guards, activation guards, and audit immutability must be designed and reviewed in DB-001C while preserving the outcomes required here.
- DB-001B infrastructure must be reviewed and approved before any business-schema migration begins.
- The authoritative DBML and this decision document must remain synchronized through explicit future architecture revisions.

None of these risks authorizes business-schema work during DB-001B.

## 22. Final readiness decision

READY FOR DB-001B INFRASTRUCTURE BOOTSTRAP
