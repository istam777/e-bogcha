# CRM Documentation

## Purpose

This folder contains the CRM workflow, PostgreSQL implementation decisions, and approved global CRM/telephony reference data for E-Bog'cha DB-001C-02.

It documents the approved implementation boundary and behavior. At this documentation stage, it does not claim that the V12 SQL migration or CRM application services have been implemented or committed.

## Authoritative document order

1. The repository-wide approved DBML remains authoritative for logical schema structure.
2. The DB-001A architecture decisions remain authoritative for platform-wide database semantics.
3. [CRM Database Decisions](CRM_DATABASE_DECISIONS.md) records DB-001C-02 database implementation decisions and its explicit architecture amendment.
4. [CRM Workflow Specification](CRM_WORKFLOW_SPECIFICATION.md) defines application workflow behavior.
5. [CRM Reference Data Proposal](CRM_REFERENCE_DATA_PROPOSAL.md) is the authoritative matrix for the 28 approved V12 global seed tuples.

## Current decision status

The 28 global reference tuples are approved exactly as recorded in `CRM_REFERENCE_DATA_PROPOSAL.md`, and V12 is authorized for implementation as a data-only Flyway migration. Its replay policy is strict plain `INSERT`: conflicts fail visibly, and no `ON CONFLICT`, merge, update, delete, or replacement behavior is permitted. Organization-scoped CRM references remain unseeded. V12 has not yet been implemented or committed at this documentation stage.

## Planned migration sequence

| Migration | Purpose | Tables |
|---|---|---:|
| `V7__create_crm_reference_schema.sql` | CRM reference structure | 6 |
| `V8__create_crm_lead_core_schema.sql` | Lead and contact core | 3 |
| `V9__create_crm_workflow_schema.sql` | Assignment, history, activity, tasks, tours, duplicates | 7 |
| `V10__create_telephony_configuration_schema.sql` | PBX configuration and telephony references | 8 |
| `V11__create_telephony_call_schema.sql` | Calls, events, recordings, lead links, webhooks | 6 |

V7-V11 contain exactly 30 CRM/telephony tables. V12 is authorized to insert only the 28 approved global reference tuples and must not contain organization-owned data.

## Deferred items

- `lead_conversions` is deferred to an unversioned CRM/Admissions boundary migration after `children` and `admission_applications` exist.
- Organization-owned CRM reference rows require an organization-bootstrap specification.
- V12 implementation remains pending; its 28 tuples and strict replay policy are approved.
- Recording retention/deletion belongs to the later storage and security implementation phase.
- CRM services and their service-level integration tests are outside this documentation-only task.
