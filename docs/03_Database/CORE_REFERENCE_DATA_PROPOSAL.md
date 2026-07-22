# Core Reference Data Proposal

Status: APPROVED

## 1. Purpose

This document records the approved fixed identity and value matrix for the minimum system-owned core reference data required by DB-001A and the delivery status of its V13 implementation.

The matrix contains exactly 16 rows across five implemented global lookup tables. After explicit tuple approval, V13 implemented the matrix without changing an existing migration.

## 2. Architecture authority

`DB-001A_architecture_decisions.md` establishes that system-owned global reference rows:

- use fixed UUID constants and immutable codes;
- are inserted through versioned seed migrations;
- do not contain organization-scoped bootstrap data; and
- receive their exact UUID constants through an explicit DB-001C approval.

The authoritative DBML and V4 schema define the columns and globally unique code constraints for the five tables. UUID/code identities become immutable only after tuple approval and release. Display names, flags, sort orders, and other mutable values may subsequently change only through a later reviewed migration.

## 3. Confirmed PR finding

The original PR #2 finding identified that V4 created these tables but V1-V12 left them empty and the integration test required them to remain empty. That behavior conflicted with DB-001A's minimum system-owned seed policy.

V13 now appears to address the technical concern by inserting the approved fixed identities. GitHub review thread `PRRT_kwDOTcawyc6S0eZR` remains current and formally unresolved; no reply or resolution has occurred.

## 4. Included tables

The proposal is limited to:

1. `languages` — 2 rows;
2. `gender_types` — 2 rows;
3. `relationship_types` — 3 rows;
4. `document_types` — 6 rows; and
5. `document_verification_statuses` — 3 rows.

Total: 5 tables and 16 rows.

## 5. Explicit exclusions

- `nationalities` is excluded because DB-001A defines no minimum system-owned nationality code.
- Organization- and branch-owned rows are excluded.
- Organization-owned CRM references and runtime PBX data remain unseeded.
- Admissions, Finance, Billing, ERP, and other deferred-module codes are excluded, including codes for tables not implemented in V1-V12.
- The proposal contains no timestamp, environment-specific value, organization UUID, or branch UUID.

## 6. Complete proposed tuple matrix

The row and table order below is normative. Every value is approved exactly as documented.

### `languages` — 2 rows

| `id` | `code` | `name` | `is_active` | `sort_order` |
|---|---|---|---:|---:|
| `fb5c59da-9ce9-4b1c-8093-708df6dff228` | `UZ` | Uzbek | true | 10 |
| `a5743740-acbf-4356-b079-b6b9fe75f517` | `RU` | Russian | true | 20 |

### `gender_types` — 2 rows

| `id` | `code` | `name` | `is_active` |
|---|---|---|---:|
| `82cc170a-af63-4aff-8777-b59b86fd79a4` | `MALE` | Male | true |
| `5433e6c3-5497-4d02-9390-1a0617a0cba8` | `FEMALE` | Female | true |

### `relationship_types` — 3 rows

| `id` | `code` | `name` | `is_active` |
|---|---|---|---:|
| `0021c600-8837-4088-91d6-a8d664c4101f` | `FATHER` | Father | true |
| `de3ccd45-2fa6-4edc-bfde-bba909cd9d82` | `MOTHER` | Mother | true |
| `48dba5fe-77b8-4df7-a691-3a6b77614ed8` | `GUARDIAN` | Guardian | true |

### `document_types` — 6 rows

| `id` | `code` | `name` | `applies_to` | `is_active` |
|---|---|---|---|---:|
| `6c75f6d0-6ec3-4092-b859-94ee08425967` | `BIRTH_CERTIFICATE` | Birth Certificate | `CHILD` | true |
| `a77a89b3-371f-4caf-bdcb-6d502bc3b720` | `PASSPORT` | Passport | `PERSON` | true |
| `dd214a9e-daaa-42a6-887f-a2f107cf3e2f` | `ID_CARD` | ID Card | `PERSON` | true |
| `8905ff1c-b3e2-406b-85bc-eed1e27dad02` | `PINFL` | PINFL | `PERSON` | true |
| `7642acca-0b62-4a06-b9be-dcc573391ba5` | `MEDICAL_CERTIFICATE` | Medical Certificate | `CHILD` | true |
| `804ce20a-6653-4ac5-8e4a-86f8bbcc544d` | `PHOTO` | Photo | `PERSON` | true |

### `document_verification_statuses` — 3 rows

| `id` | `code` | `name` | `is_final` | `is_active` |
|---|---|---|---:|---:|
| `0c412202-9976-49d0-96e5-c010c5caf731` | `PENDING` | Pending | false | true |
| `40e8acf6-186a-4bc5-b050-0ab076032f20` | `VERIFIED` | Verified | true | true |
| `90a8cec4-b402-466d-a6d5-4474517d9312` | `REJECTED` | Rejected | true | true |

## 7. UUID validation

The matrix contains 16 newly generated lowercase canonical UUIDv4 constants. Each uses the UUID version-4 nibble and an RFC 4122-compatible variant, and all 16 values are distinct.

Before this document was written, every proposed UUID was checked against the repository, including the fixed identities in V3 and V12, and no collision existed. These proposal UUIDs must remain unchanged during review unless a reviewer explicitly rejects a tuple. No database UUID-generation expression is proposed.

## 8. Field-value decisions

All rows are approved as active because they are the minimum available system vocabulary.

For `languages`, `sort_order` is explicitly seeded as 10 for `UZ` and 20 for `RU`; V13 must not rely on the database default.

No authoritative `document_types.applies_to` vocabulary exists in the DBML or current approved architecture documentation. This proposal therefore introduces the following minimal application-owned vocabulary:

- `CHILD`: limited to child or student records.
- `PERSON`: usable for either child or adult person records.

The approved assignments are `BIRTH_CERTIFICATE` and `MEDICAL_CERTIFICATE` to `CHILD`, with `PASSPORT`, `ID_CARD`, `PINFL`, and `PHOTO` assigned to `PERSON`. The schema has no enum or CHECK constraint for this field, and this proposal does not add one. The vocabulary and every assignment are implemented by V13 as approved. Later vocabulary expansion requires application and documentation review.

For verification statuses, `VERIFIED` and `REJECTED` are approved terminal outcomes (`is_final = true`), while `PENDING` is non-final (`is_final = false`). Every row is explicitly active.

## 9. Implemented V13 migration contract

The implemented migration is:

`backend/src/main/resources/db/migration/V13__seed_core_reference_data.sql`

V13:

- be data-only;
- insert exactly 16 rows into exactly five tables;
- use ordinary strict `INSERT` statements with explicit column lists;
- use the exact approved UUIDs, codes, names, flags, sort orders, and `applies_to` values;
- contain no DDL or generated UUID;
- contain no organization- or branch-scoped data; and
- visibly fail when a UUID or globally unique code conflicts.

No existing migration may be edited. Any later identity or value change requires a later explicitly reviewed versioned migration and must not rewrite V13.

## 10. Strict conflict and replay policy

V13 prohibits:

- `ON CONFLICT`;
- `MERGE`;
- `WHERE NOT EXISTS` or any other conditional insertion;
- `UPDATE`;
- delete-and-reinsert behavior;
- exception swallowing; and
- temporary replacement logic.

Ordinary strict insertion makes drift visible. A UUID or unique-code collision must fail migration with SQLSTATE `23505`. Flyway provides replay safety by recording the successful version once; V13 must not simulate idempotency by suppressing conflicts.

## 11. Integration-test contract

The implemented `backend/src/test/java/uz/oxukids/ebogcha/DatabaseInfrastructureIntegrationTest.java` coverage proves:

- Flyway applies V1-V13 and reports no pending migration;
- exactly 16 new rows exist with the exact UUID/code/name/value matrix;
- all 16 UUIDs are distinct;
- code-to-UUID and UUID-to-code mappings are bijective;
- identity conflicts fail visibly with SQLSTATE `23505`;
- structural totals remain unchanged;
- V3 and V12 identities remain unchanged;
- `nationalities` remains unseeded;
- organization-owned CRM references and runtime PBX data remain unseeded; and
- existing test fixtures never delete fixed seed rows.

The obsolete five-table zero-row expectation was removed only for the V13 target tables. Zero-row assertions remain for `nationalities`, organization-owned references, runtime PBX data, and other intentionally empty tables.

## 12. Delivery progress and remaining gates

The tuple approval, approval-document commit, V13 implementation, integration-test update, independent read-only implementation review, implementation commit, push, and remote verification are complete.

The remaining gates are:

1. review this documentation-only change;
2. commit and push this documentation update;
3. update PR #2's description through a dedicated external-action task;
4. reply to the review concern with the V13 commit and validation evidence;
5. resolve the review thread only after verification;
6. perform final remote and PR verification;
7. determine merge readiness; and
8. merge only through a separately authorized task.

## 13. Approval record

Approval decision: **APPROVE ALL 16 CORE REFERENCE TUPLES**

Approved scope: exactly 16 rows across five tables:

- `languages`;
- `gender_types`;
- `relationship_types`;
- `document_types`; and
- `document_verification_statuses`.

The approved UUID, code, name, flag, sort-order, and `applies_to` values are fixed in `backend/src/main/resources/db/migration/V13__seed_core_reference_data.sql`. V13 inserts exactly the approved 16 fixed tuples across five tables with strict ordinary `INSERT` statements and no conflict suppression. It contains no `nationalities` or organization-owned reference data.

Delivery and validation record:

- DB-001C-05D = APPROVED. The independent read-only implementation review recorded no BLOCKER, HIGH, MEDIUM, or LOW implementation finding.
- Full Gradle validation ran 75 tests. Test result: 75 passed, 0 failed, 0 errors, 0 skipped.
- Testcontainers and Flyway used PostgreSQL 17.10.
- Strict duplicate-code behavior is covered through SQLSTATE `23505`, and the existing V3 and V12 seed coverage remains intact.
- Approval commit: `b0ba7175bc81eaea7dff2b81ef1db8f9e81ac694` (`docs: approve core reference seed data`).
- V13 implementation commit: `aaaee9c92600d3c36de37d825c778114e16d9e3c` (`feat(db): seed core reference data`).
- Both commits were pushed normally, without force, to `origin/feature/database-foundation`.
- Branch: `feature/database-foundation`. Local and remote head: `aaaee9c92600d3c36de37d825c778114e16d9e3c`. Ahead/behind after push: 0 behind and 0 ahead.
- PR #2 is open, is not a draft, contains the V13 implementation commit, and has head `aaaee9c92600d3c36de37d825c778114e16d9e3c`. GitHub reported it mergeable during DB-001C-05H.
- GitHub checks: not configured. There is no formal approval or change-request review decision.
- PR description update: pending.
- Review thread `PRRT_kwDOTcawyc6S0eZR` remains unresolved and not outdated. V13 appears to address its technical concern, but no reply or formal resolution has occurred.

## 14. Current status

Status: APPROVED

The exact 16-row matrix remains approved and is implemented, independently reviewed, committed, pushed, and remotely verified as recorded above. PR #2's description update, review-thread response and formal resolution, final re-review, and merge decision remain pending. PR #2 is not yet declared ready to merge, and merge requires a separately authorized task.
