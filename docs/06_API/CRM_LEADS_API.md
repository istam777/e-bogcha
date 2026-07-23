# CRM Leads API

## Contract

The CRM lead API uses the base path `/api/v1/crm/leads` and JSON request and
response bodies. Timestamps are ISO-8601 instants in UTC.

The values in this document are fictional. Authentication is deferred. Until
it is implemented, commands that require an actor use:

```http
X-Actor-User-Id: 44444444-4444-4444-8444-444444444444
```

The header identifies an existing user but is not authentication. A future
authentication layer must replace the caller-supplied header without changing
the application use cases.

## Create a lead

`POST /api/v1/crm/leads`

```json
{
  "leadId": "11111111-1111-4111-8111-111111111111",
  "organizationId": "22222222-2222-4222-8222-222222222222",
  "branchId": "33333333-3333-4333-8333-333333333333",
  "source": "PHONE",
  "parentOrGuardianName": "Fictional Guardian",
  "displayPhone": "+998 90 123 45 67"
}
```

A successful request returns `201 Created`, a
`Location: /api/v1/crm/leads/11111111-1111-4111-8111-111111111111` header,
and:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "organizationId": "22222222-2222-4222-8222-222222222222",
  "branchId": "33333333-3333-4333-8333-333333333333",
  "source": "PHONE",
  "status": "NEW",
  "parentOrGuardianName": "Fictional Guardian",
  "displayPhone": "+998 90 123 45 67",
  "ownerOperatorId": null,
  "lostReasonId": null,
  "createdAt": "2026-07-23T08:00:00Z",
  "firstContactDueAt": "2026-07-24T08:00:00Z",
  "duplicateCandidateIds": [
    "55555555-5555-4555-8555-555555555555"
  ]
}
```

Duplicate candidates are returned in deterministic UUID order and do not block
creation. Supported sources are `SOCIAL_MEDIA`, `PHONE`, and `WALK_IN`.

## Get a lead

`GET /api/v1/crm/leads/{leadId}`

A successful request returns `200 OK` and the lead fields shown above, excluding
`duplicateCandidateIds`. An unknown lead returns `404`.

## Accept a lead

`POST /api/v1/crm/leads/{leadId}/accept`

The request requires `X-Actor-User-Id` and has no body. A successful request
returns `200 OK` with the updated lead. Existing organization, branch-access,
idempotency, and first-operator-wins rules remain authoritative.

## Change lead status

`POST /api/v1/crm/leads/{leadId}/status-transitions`

The request requires `X-Actor-User-Id`.

```json
{
  "targetStatus": "LOST",
  "lostReasonId": "66666666-6666-4666-8666-666666666666"
}
```

A successful request returns `200 OK` with the updated lead. `lostReasonId` is
required for `LOST`, is cleared when leaving `LOST`, and is otherwise optional.
A same-status request is idempotent and does not append status history.

Supported statuses are `NEW`, `CONTACTED`, `TOUR_PLANNED`, `SUCCESSFUL`,
`NO_SHOW`, `LOST`, and `ARCHIVED`. The domain transition policy determines
which transitions are allowed.

## Errors

Errors use `application/problem+json` and always contain `type`, `title`,
`status`, `detail`, `instance`, `code`, and `timestamp`.

```json
{
  "type": "urn:problem:crm-lead-not-found",
  "title": "CRM lead not found",
  "status": 404,
  "detail": "The requested lead does not exist.",
  "instance": "/api/v1/crm/leads/11111111-1111-4111-8111-111111111111",
  "code": "CRM_LEAD_NOT_FOUND",
  "timestamp": "2026-07-23T08:00:00Z"
}
```

| HTTP status | Code | Meaning |
|---:|---|---|
| 400 | `CRM_REQUEST_INVALID` | Malformed JSON, invalid UUID, enum, phone, or request validation |
| 400 | `CRM_ACTOR_INVALID` | Missing or malformed `X-Actor-User-Id` |
| 403 | `CRM_BRANCH_ACCESS_DENIED` | Actor lacks access to the lead branch |
| 404 | `CRM_LEAD_NOT_FOUND` | Lead does not exist |
| 409 | `CRM_LEAD_ALREADY_OWNED` | Another operator owns the lead |
| 409 | `CRM_LEAD_DUPLICATE` | Lead identity conflicts with an existing lead |
| 422 | `CRM_STATUS_TRANSITION_INVALID` | Domain status transition is not allowed |
| 422 | `CRM_LOST_REASON_REQUIRED` | A `LOST` transition has no reason |
| 422 | `CRM_BRANCH_ORGANIZATION_INVALID` | Branch is outside the supplied organization |
| 422 | `CRM_REFERENCE_INVALID` | Required CRM reference data is unavailable |
| 500 | `CRM_INTERNAL_ERROR` | Sanitized internal or persistence failure |

Internal errors never expose SQL, stack traces, phone numbers, guardian names,
credentials, or underlying database exception details.
