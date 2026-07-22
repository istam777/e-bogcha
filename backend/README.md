# E-Bog'cha backend infrastructure

This directory contains the Spring Boot backend bootstrap and the implemented PostgreSQL database foundation. Flyway is the schema authority, and PostgreSQL Testcontainers integration tests validate the migrations. Production business services and APIs remain deferred unless explicitly implemented in a later task.

## Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- Windows PowerShell

Use the committed Gradle Wrapper. A global Gradle installation is not required.

Spring Boot 3.5.16 is pinned because the approved stack requires Spring Boot 3. A future Spring Boot major-version upgrade requires a separate architecture decision.

## Prepare the local environment

From the repository root, create the ignored local environment file:

```powershell
Copy-Item .env.example .env
```

Replace the placeholder local password in `.env`. Never commit `.env` or put production credentials in it.

Docker Compose reads `.env` through `--env-file`. To run Spring Boot from the same PowerShell session, load the non-comment entries without printing their values:

```powershell
Get-Content .env |
    Where-Object { $_ -match '^[^#][^=]*=' } |
    ForEach-Object {
        $name, $value = $_ -split '=', 2
        Set-Item -Path "Env:$name" -Value $value
    }
```

## Start and inspect PostgreSQL

```powershell
docker compose --env-file .env -f infrastructure/docker/compose.yml up -d postgres
docker compose --env-file .env -f infrastructure/docker/compose.yml ps postgres
```

The service is ready when its health status is `healthy`.

Stop PostgreSQL without deleting its data:

```powershell
docker compose --env-file .env -f infrastructure/docker/compose.yml down
```

## Build, test, and run

Confirm the wrapper and runtime:

```powershell
.\backend\gradlew.bat --version
```

Run all tests. The integration test starts PostgreSQL 17 through Testcontainers and does not use H2:

```powershell
.\backend\gradlew.bat clean test
```

Run the application after PostgreSQL is healthy and the `.env` values have been loaded into the current PowerShell session:

```powershell
.\backend\gradlew.bat bootRun
```

Check application health in another terminal. Replace the port if `APP_PORT` differs from 8080:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Only the `health` and `info` actuator endpoints are exposed.

## Recreate a disposable local database

The following operation permanently removes only this Compose project's named PostgreSQL volume. Use it only for disposable local data, never for shared or production data:

```powershell
docker compose --env-file .env -f infrastructure/docker/compose.yml down
docker volume rm e-bogcha_postgres_data
docker compose --env-file .env -f infrastructure/docker/compose.yml up -d postgres
```

Do not run destructive volume deletion against shared or production systems. Normal shutdown must use `docker compose ... down` without `--volumes`.

## Migration boundary

Flyway scans `classpath:db/migration` and applies the implemented database foundation through V12:

- `V1__create_foundation_schema.sql`: Core and IAM foundation
- `V2__enforce_audit_log_immutability.sql`: audit-log immutability
- `V3__seed_foundation_reference_data.sql`: foundation reference data
- `V4__create_core_reference_schema.sql`: core reference schema
- `V5__create_identity_and_staff_schema.sql`: identity and staff schema
- `V6__create_file_and_settings_schema.sql`: Files, Audit, and Settings schema
- `V7__create_crm_reference_schema.sql`: CRM reference schema
- `V8__create_crm_lead_core_schema.sql`: CRM lead core schema
- `V9__create_crm_workflow_schema.sql`: CRM workflow schema
- `V10__create_telephony_configuration_schema.sql`: Telephony configuration schema
- `V11__create_telephony_calls_schema.sql`: Telephony calls schema
- `V12__seed_crm_and_telephony_reference_data.sql`: approved global CRM and telephony reference data

This delivery implements the database foundation only. It does not implement production business services or APIs. Admissions and later modules remain outside this database delivery.
