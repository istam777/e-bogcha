# CRM and Telephony Reference Data Proposal

Status: READY FOR APPROVAL
Scope: Proposed global rows for the future reserved V12 migration

## 1. Proposal rules

This document proposes exactly the global codes approved by DB-001A. It does not insert data and does not authorize V12.

The 28 proposed identifiers are deterministic, structurally valid UUIDv4 values. They were derived from the stable input `e-bogcha:crm-reference:<table>:<code>` using SHA-256, taking the first 128 bits and setting the RFC 4122 version to 4 and variant bits to `10`. Once approved and released, each UUID and code is immutable.

Canonical names are concise English system labels. Every table below has a proposed value for every required field and is marked **READY FOR APPROVAL**, not approved.

## 2. `lead_task_statuses` — READY FOR APPROVAL

| UUID | Code | Name | `is_closed` | `is_active` | Rationale |
|---|---|---|---:|---:|---|
| `e17553cb-84e0-4f6b-9d89-daf1171800c6` | `OPEN` | Open | false | true | Work has not been completed or cancelled. |
| `a711f203-49d7-43fe-ae64-6b3b2b662702` | `IN_PROGRESS` | In Progress | false | true | Work is actively proceeding. |
| `396c77c7-c5d3-4e56-9f9a-b64019872b91` | `COMPLETED` | Completed | true | true | Terminal successful task state. |
| `e8d7cdc6-6e26-4d7b-b995-f80a46d148eb` | `CANCELLED` | Cancelled | true | true | Terminal cancelled task state. |

## 3. `lead_activity_types` — READY FOR APPROVAL

| UUID | Code | Name | `is_active` | Rationale |
|---|---|---|---:|---|
| `72af8200-7d11-448b-a40c-fd22f64dc69c` | `CALL` | Call | true | Lead-related call activity. |
| `de43e843-46cf-43d8-9e1e-0930bd3f2b79` | `NOTE` | Note | true | Note activity in the lead timeline. |
| `7a1c856c-c25a-461d-8b21-aae646911d51` | `STATUS_CHANGE` | Status Change | true | Current status changed. |
| `80455b96-6b6f-4ae8-a6d8-414c5773cf55` | `ASSIGNMENT` | Assignment | true | Lead assignment or transfer. |
| `9928bbba-0aba-4205-b9de-6fef84ff5865` | `TOUR` | Tour | true | Tour scheduling or tour-related activity. |
| `5a8ad0a2-8683-489f-b71f-788887c65744` | `SYSTEM` | System | true | System-originated timeline activity. |

## 4. `call_directions` — READY FOR APPROVAL

| UUID | Code | Name | Rationale |
|---|---|---|---|
| `12cdc1a3-4e6a-4f6d-a825-ff8bc5fa9391` | `INBOUND` | Inbound | Call received by the PBX. |
| `440c2104-1afc-4d3d-bf60-f25f3609e8ba` | `OUTBOUND` | Outbound | Call initiated through the PBX. |

## 5. `call_dispositions` — READY FOR APPROVAL

| UUID | Code | Name | `is_missed` | Rationale |
|---|---|---|---:|---|
| `6553de3a-4c44-485f-93b3-fb767866cb4b` | `ANSWERED` | Answered | false | The call was answered. |
| `5f73f656-69fe-4076-a922-e66f0a09be4a` | `MISSED` | Missed | true | The approved explicit missed-call classification. |
| `7160c287-62ae-4522-823f-74c37e3fb454` | `BUSY` | Busy | false | The destination reported busy; it is distinct from explicit `MISSED`. |
| `472be4fc-cf50-4dc2-98ea-e1d51e6d141b` | `REJECTED` | Rejected | false | The call was explicitly rejected, not classified as `MISSED`. |
| `c196bc93-f295-4edf-80e8-5a6a08e34f2e` | `FAILED` | Failed | false | A technical failure is distinct from explicit `MISSED`. |
| `e55118ed-1e56-440e-9dfb-415c96ae2533` | `NO_ANSWER` | No Answer | false | No-answer remains distinct from explicit `MISSED`; direction-aware reporting may group it separately. |

The `is_missed` flag identifies only the explicit `MISSED` disposition. Other dispositions such as `NO_ANSWER`, `BUSY`, `REJECTED`, and `FAILED` may occur in either inbound or outbound calls and therefore remain globally false. Reporting an inbound missed call must combine call direction and disposition in the application or query layer.

## 6. `call_event_types` — READY FOR APPROVAL

| UUID | Code | Name | Rationale |
|---|---|---|---|
| `9fbd9a57-37ff-40bb-aaab-fa184c5260bc` | `STARTED` | Started | Call session began. |
| `eabb3ab9-78fb-4b04-9222-9443c8883f12` | `RINGING` | Ringing | Call entered ringing state. |
| `ac5d2745-01b3-45e8-8e5b-bd55fea40889` | `ANSWERED` | Answered | Call was answered. |
| `23d85d99-98db-4ff4-8fee-96573bb36579` | `ENDED` | Ended | Call session ended. |
| `0123d4ef-f3a3-4efc-8d76-cfea8c345830` | `RECORDING_AVAILABLE` | Recording Available | Recording metadata became available. |

## 7. `webhook_statuses` — READY FOR APPROVAL

| UUID | Code | Name | `is_final` | Rationale |
|---|---|---|---:|---|
| `182f569e-d993-4ed8-b26c-1c4d3c333bb8` | `RECEIVED` | Received | false | Accepted but not yet processed. |
| `cbb743e8-4f1a-4776-96fe-2523ef0c4638` | `PROCESSING` | Processing | false | Processing is in progress. |
| `6ec1db81-3e04-481c-b4e1-aaffeacf58d0` | `PROCESSED` | Processed | true | Processing completed successfully. |
| `cc8204f8-f7a9-4db8-8cb4-87e5d53aacd8` | `FAILED` | Failed | true | Processing reached a terminal failure state. A retry creates or performs an explicit state transition. |
| `e6c3c3ae-eb91-44c2-ae6b-af1a9f67bf85` | `IGNORED` | Ignored | true | Event was deliberately not processed. |

## 8. Organization-owned references excluded from V12

The following are created only during organization bootstrap after an organization UUID is available:

- `lead_sources`: known default codes `SOCIAL_MEDIA`, `PHONE`, `WALK_IN`;
- `lead_statuses`: approved workflow codes exist, but fixed UUIDs, names, pipeline order, and flags require a separate bootstrap specification;
- `lost_reasons`: no approved default codes; and
- `tour_outcomes`: known default codes `CONTRACT`, `THINKING`, `NO_SHOW`.

These rows must not be globally seeded. This proposal contains no organization UUID, credential, endpoint, phone number, personal data, or environment-specific value.

## 9. Approval checklist

- Approve all 28 UUID/code/name tuples.
- Approve task `is_closed` values.
- Approve all `is_active = true` values.
- Confirm that the approved `call_dispositions.is_missed` mapping is documented and validated.
- Approve `webhook_statuses.is_final` values.
- Only after approval may V12 be authored and reviewed.
