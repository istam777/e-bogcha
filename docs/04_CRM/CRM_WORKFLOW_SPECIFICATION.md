# CRM Workflow Specification

Status: Proposed authoritative specification for DB-001C-02 documentation review
Project: E-Bog'cha V1
Company: Oxu Kids

## 1. Purpose and authority

This document defines the approved CRM and telephony workflow behavior that the application and database must preserve. The approved DBML remains authoritative for table names, columns, types, nullability, keys, foreign keys, timestamps, and declared indexes. `CRM_DATABASE_DECISIONS.md` is authoritative for the implementation decisions explicitly recorded for DB-001C-02.

DB-001C-02 implements the 30 CRM and telephony tables that do not depend on Admissions. It does not implement Admissions, contracts, Finance, ERP, or other business modules.

## 2. Lead creation and initial-contact SLA

A lead is created with a branch, source, current status, parent or guardian name, at least one phone, and one selected primary phone. Organization-scoped source and status rows must belong to the organization that owns the selected branch.

Phone input is normalized by the application before persistence. The canonical value is stored in `lead_phones.normalized_phone`; the user-entered representation is stored in `lead_phones.display_phone`.

The initial-contact SLA is 24 hours:

- the application calculates `leads.first_contact_due_at` when creating the lead;
- `leads.first_contact_at` is set when a qualifying first contact is recorded;
- breach state is derived from these timestamps;
- no database trigger, scheduled database job, or hardcoded SQL timer is used; and
- the duration should later become configurable through `application_settings`, but no organization-specific setting is seeded by DB-001C-02.

The definition of a qualifying first contact belongs to the future CRM application-service specification.

## 3. Phone normalization and duplicate discovery

Phone normalization is an application-layer responsibility:

- use Google libphonenumber or a compatible standards-compliant library;
- use `UZ` as the default region;
- accept common valid local and international formatting;
- store the canonical result in E.164 format, for example `+998901234567`;
- reject unparseable or invalid public telephone numbers; and
- do not normalize with SQL functions or database triggers.

Duplicate discovery is organization-scoped through:

```text
lead_phones -> leads -> branches -> organizations
```

The same normalized phone may exist in different organizations. Duplicate candidate lookup uses the approved `lead_phones.normalized_phone` index plus the organization join. `normalized_phone` is deliberately not unique: duplicate leads are identified and linked, not prevented at insertion.

## 4. Primary-phone workflow

A lead may have multiple phone rows. At most one may have `is_primary = true`; the database enforces this with a partial unique index on `lead_phones(lead_id) WHERE is_primary`.

Multiple non-primary rows and temporarily having zero primary rows are database-valid. Before a lead create or update transaction completes, the application must require at least one phone and exactly one selected primary phone. No trigger is used to require the existence of a primary phone.

## 5. Lead assignment and ownership

A lead may retain assignment history, but it may have at most one active assignment. An active assignment is a `lead_assignments` row where `ended_at IS NULL`.

- The first operator who accepts the lead becomes its initial owner.
- The active `lead_assignments` row is the authoritative current assignment.
- `leads.owner_user_id` is a projection/cache for efficient current-owner reads.
- Assignment acceptance updates both representations in one application transaction.
- Transfer ends the current assignment and inserts the replacement assignment atomically in the same transaction.
- No synchronization trigger is created.
- Historical assignment rows coexist with at most one active row.

The application must validate that the assigned user has access to the lead branch.

## 6. Status workflow

`lead_statuses` is dynamic and organization-owned. `leads.status_id` holds the current status. Every status change inserts a corresponding `lead_status_history` row, and both changes occur in one transaction.

Status history is append-only through application permissions and service design. DB-001C-02 does not add an UPDATE/DELETE rejection trigger. Authorized users may reopen a lead; reopening creates another history row. SQL does not hardcode a transition graph.

Application consistency rules are:

- a destination status with `is_lost = true` requires `lost_reason_id`;
- a destination status with `is_archived = true` sets `archived_at`;
- reopening to a status that is not lost clears `lost_reason_id`; and
- reopening to a status that is not archived clears `archived_at`.

The status, lost reason, and branch must belong to the same organization.

## 7. Duplicate-link workflow

`lead_duplicates` is directional:

- `lead_id` is the duplicate or candidate lead; and
- `duplicate_of_lead_id` is the canonical lead.

The database rejects a self-link and retains the DBML unique directed pair. The application rejects reverse pairs, cross-organization links, and any resolution that makes the canonical lead resolve back to the duplicate. Resolution is transactional.

The approved DBML does not model physical row merging. Duplicate leads remain available for audit and history. No delete cascade, merge table, trigger, or canonical-pair expression index is introduced.

## 8. Tour workflow

Tours are created through authorized Sales Manager workflows. A tour uses the existing `tours` fields; there is no separate status table and no database-enforced one-active-tour rule.

- `scheduled_at` is the planned time.
- Rescheduling updates `scheduled_at` on the same row and inserts a `lead_activities` timeline entry.
- There is no separate reschedule-history table.
- `attended_at` records actual attendance.
- `outcome_id` remains null until the workflow records an outcome.
- No-show is represented by the organization-owned `tour_outcomes` code `NO_SHOW`.
- Attendance, outcome, scheduling-time, branch, lead, outcome, and Sales Manager consistency are application-validated.

No tour workflow `CHECK` constraint or trigger is added.

## 9. Telephony workflow

The telephony foundation supports Grandstream integration without placing provider secrets or recording binaries in PostgreSQL.

- Endpoints and credentials are runtime configuration.
- `pbx_configs.credential_secret_reference` and `sip_accounts.secret_reference` store references only.
- `call_sessions` is the PBX-scoped call identity.
- `call_events` stores timeline events; its nullable `external_event_id` remains indexed and non-unique.
- `webhook_events` provides PBX-scoped webhook idempotency.
- `lead_calls` links calls to leads.
- `call_recordings` stores metadata and optional stored-file or URL references.
- Recording binary data is prohibited in PostgreSQL.
- A recording may temporarily have both `stored_file_id` and `recording_url` null during asynchronous processing.
- `external_recording_id` remains indexed and non-unique.
- No database cleanup trigger, retention trigger, scheduled job, duration check, or timestamp-order check is created.

Initial participant-role codes are an application contract, not database reference data or constraints:

- `CALLER`
- `CALLEE`
- `AGENT`
- `IVR`
- `QUEUE`
- `TRANSFER_TARGET`
- `UNKNOWN`

## 10. Application validation

The application rejects:

- negative call duration;
- negative recording duration;
- `answered_at` before `started_at`;
- `ended_at` before `started_at`;
- participant `left_at` before `joined_at`; and
- tour `attended_at` before logical scheduling where applicable.

Each cross-scope relationship is validated in the same transaction before insert or update:

- lead branch organization matches source and status organizations;
- lost reason and tour outcome belong to the lead organization;
- tour branch belongs to the lead organization;
- PBX branch belongs to the PBX organization;
- duplicate leads belong to the same organization;
- assigned users have access to the applicable branch; and
- the Sales Manager has access to the tour branch.

DB-001C-02 does not redesign the DBML with composite FKs and does not add cross-table triggers.

## 11. Recording security

- Dynamic RBAC controls recording access.
- Only authorized roles may obtain recording URLs.
- Signed recording URLs must be short-lived.
- Raw PBX credentials, secrets, and secret values must never enter logs.
- Persisted webhook metadata and payload-derived data must be sanitized.
- General operational logs must mask phone numbers.
- Recording access must generate an audit record where supported by the application.
- Retention is configurable; no fixed retention period is approved here.
- Storage deletion and retention enforcement are deferred to the storage and security implementation phase.

## 12. CRM-to-Admissions boundary

`lead_conversions` is not part of DB-001C-02. It has mandatory FKs to Admissions-owned `children` and `admission_applications`, which do not exist in V1-V6. It will be created in a later, unversioned CRM/Admissions boundary migration after both targets exist.

No placeholder Admissions table or forward FK is permitted. The approved DBML supports converting one lead for multiple children and remains authoritative for that later boundary.
