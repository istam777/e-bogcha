# E-Bog'cha

Internal kindergarten management platform for Oxu Kids. The system will manage the complete operational flow from CRM lead acquisition through admissions, payment, student enrollment, and later ERP processes.

> Project status: architecture and repository foundation. Application bootstrap has not yet been implemented.

## Approved architecture

| Layer | Technology |
|---|---|
| Frontend | React, TypeScript, Vite |
| Backend | Java 21, Spring Boot 3.x |
| Database | PostgreSQL 17 |
| Cache | Redis |
| Authentication | Spring Security and JWT |
| Database migrations | Flyway |
| File storage | MinIO or S3-compatible storage |
| Real-time communication | WebSocket where required |
| Infrastructure | Docker Compose and Nginx |
| CI/CD | GitHub Actions |

The backend follows a modular-monolith architecture. Search infrastructure such as Elasticsearch will be introduced only when a documented requirement and measured need justify it.

## Version 1 scope

- Core Platform
- CRM
- Admissions
- ERP foundation
- Telephony integration foundation
- Dynamic RBAC, settings, audit, notifications, and file management foundations

Primary business flow:

```text
Lead -> Tour -> Contract -> Payment -> Student enrollment
```

## Repository structure

```text
e-bogcha/
├── backend/                  # Spring Boot application
├── frontend/                 # React application
├── database/                 # Database assets and supporting scripts
├── docs/
│   ├── 01_Project_Foundation
│   ├── 02_System_Architecture
│   ├── 03_Database
│   ├── 04_CRM
│   ├── 05_Admissions
│   ├── 06_API
│   ├── 07_UI_UX
│   ├── 08_Development
│   └── 09_Deployment
├── branding/                 # Logos and design assets
├── infrastructure/
│   ├── docker/
│   ├── nginx/
│   └── scripts/
├── scripts/
└── .github/
```

## Branch strategy

| Branch | Purpose | Target |
|---|---|---|
| `main` | Production-ready code | — |
| `develop` | Integration branch | `main` |
| `feature/*` | New features | `develop` |
| `bugfix/*` | Non-production fixes | `develop` |
| `hotfix/*` | Urgent production fixes | `main` |

Implementation work must be performed through scoped branches and pull requests. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

- [Project Foundation](docs/01_Project_Foundation/README.md)
- [System Architecture](docs/02_System_Architecture/README.md)
- [Database Architecture](docs/03_Database/README.md)
- [Development Standards](docs/08_Development/README.md)
- [Security Policy](SECURITY.md)

## Local development

Local start commands will be added during the Core Platform Bootstrap task together with the Maven wrapper, frontend package configuration, Docker Compose stack, health checks, and verified setup instructions. Commands are intentionally not documented before they exist and are tested.
