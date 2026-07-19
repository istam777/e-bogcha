# E-Bog'cha Project Foundation

## Product identity

- Company: Oxu Kids
- Product: E-Bog'cha
- Deployment model: internal platform for one organization with multiple branches
- Initial scale: approximately 500 children and 50–60 employees

## Product objective

E-Bog'cha centralizes kindergarten operations in one controlled system and creates a reliable flow from lead acquisition to admission, payment, enrollment, and later operational ERP processes.

## Release scope

### Version 1

- Core Platform
- CRM
- Admissions
- ERP skeleton
- Telephony integration foundation
- Audit, settings, notifications, and file management foundations

### Later releases

- Finance
- Attendance
- Kitchen and food operations
- Medical
- Warehouse
- HR and KPI
- Parent portal or mobile application
- Analytics and reporting

## Primary business flow

```text
Lead -> Tour -> Contract -> Payment -> Student enrollment
```

A lead may originate from a social form, phone call, or walk-in. A contract may also be created directly without a CRM lead. A child becomes an active ERP student only after the required payment condition is satisfied.

## Core product principles

- Internal product, not SaaS in Version 1.
- Multiple branches are supported from the beginning.
- Business statuses, tariffs, discounts, permissions, and relevant reference data are configurable.
- Every sensitive operation is authorized and audited.
- A single source of truth is maintained for each business entity.
- Personal data belonging to children and families is protected by design.

## Roles in delivery

- Product Owner: defines business priorities and approves behavior.
- Architecture and review: defines standards, specifications, and acceptance criteria.
- Codex: implements scoped engineering tasks.
- GitHub: source control, review, and delivery history.

## Delivery method

Work is completed incrementally:

```text
Requirement -> Specification -> Architecture -> Task -> Implementation -> Review -> Test -> Merge
```

Each task must have explicit acceptance criteria and a definition of done before implementation begins.
