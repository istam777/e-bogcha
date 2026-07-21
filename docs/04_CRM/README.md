# CRM Documentation

## Purpose

This folder contains the CRM workflow, PostgreSQL implementation decisions, and proposed global CRM/telephony reference data for E-Bog'cha DB-001C-02.

It documents the approved implementation boundary and behavior. It does not claim that V7-V12 SQL migrations or CRM application services have been implemented.

## Authoritative document order

1. The repository-wide approved DBML remains authoritative for logical schema structure.
2. The DB-001A architecture decisions remain authoritative for platform-wide database semantics.
3. [CRM Database Decisions](CRM_DATABASE_DECISIONS.md) records DB-001C-02 database implementation decisions and its explicit architecture amendment.
4. [CRM Workflow Specification](CRM_WORKFLOW_SPECIFICATION.md) defines application workflow behavior.
5. [CRM Reference Data Proposal](CRM_REFERENCE_DATA_PROPOSAL.md) proposes global seed tuples and remains subject to explicit approval.

## Current decision status

The CRM workflow and database decisions needed to plan V7-V11 are documented for review. Global reference tuples are ready for approval; they are not yet approved seed data.

## Planned migration sequence

| Migration | Purpose | Tables |
|---|---|---:|
| `V7__create_crm_reference_schema.sql` | CRM reference structure | 6 |
| `V8__create_crm_lead_core_schema.sql` | Lead and contact core | 3 |
| `V9__create_crm_workflow_schema.sql` | Assignment, history, activity, tasks, tours, duplicates | 7 |
| `V10__create_telephony_configuration_schema.sql` | PBX configuration and telephony references | 8 |
| `V11__create_telephony_call_schema.sql` | Calls, events, recordings, lead links, webhooks | 6 |

V7-V11 contain exactly 30 CRM/telephony tables. V12 is reserved for approved global reference seeds and must not contain organization-owned data.

## Deferred items

- `lead_conversions` is deferred to an unversioned CRM/Admissions boundary migration after `children` and `admission_applications` exist.
- Organization-owned CRM reference rows require an organization-bootstrap specification.
- V12 requires explicit approval of the proposed 28 global tuples.
- Recording retention/deletion belongs to the later storage and security implementation phase.
- CRM services and their service-level integration tests are outside this documentation-only task.
