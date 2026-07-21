# E-Bog'cha backend infrastructure

This directory contains the DB-001B Spring Boot and database-infrastructure bootstrap. It intentionally contains no business-domain code and no business-schema Flyway migration.

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

Flyway is enabled and scans `classpath:db/migration`. DB-001B keeps that location empty, so Flyway may create only its own schema-history infrastructure. The 88-table approved business schema belongs to DB-001C after DB-001B review and approval.
