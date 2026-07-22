# E-Bog'cha

Internal kindergarten management platform for Oxu Kids. The system will manage the complete operational flow from CRM lead acquisition through admissions, payment, student enrollment, and later ERP processes.

> Project status: the Spring Boot backend bootstrap and PostgreSQL/Flyway database foundation through V12 are implemented. The database foundation covers Core/IAM, Files/Audit/Settings, CRM, and Telephony schemas, with PostgreSQL Testcontainers integration tests. Production business services and APIs, frontend workflows, Admissions, Finance, and later ERP functionality remain deferred unless explicitly implemented in a later task.

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

### Prerequisites

- Java 21 (OpenJDK or equivalent)
- Docker Desktop (with Docker Compose)
- Node.js 18+ (for frontend, when implemented)

### Setup

**Step 1:** Copy the environment template

```bash
cp .env.example .env
```

Edit `.env` and set appropriate values for local development. The default values work for most cases, but review database credentials and other sensitive settings.

**Step 2:** Start infrastructure services

```bash
docker compose --env-file .env -f infrastructure/docker/compose.yml up -d
```

This starts PostgreSQL 17 with persistent storage. Verify the container is healthy:

```bash
docker ps
```

**Step 3:** Start the backend application

```bash
cd backend
./gradlew bootRun       # Unix/macOS/Linux
.\gradlew.bat bootRun   # Windows
```

The backend API will be available at `http://localhost:8080`.

**Health check:** `http://localhost:8080/actuator/health`

### Stopping services

```bash
# Stop the backend: Ctrl+C in the terminal running bootRun

# Stop infrastructure:
docker compose --env-file .env -f infrastructure/docker/compose.yml down
```

### Running tests

```bash
cd backend
./gradlew test          # Unix/macOS/Linux
.\gradlew.bat test      # Windows
```

Tests use Testcontainers and do not require the Docker Compose stack to be running.
