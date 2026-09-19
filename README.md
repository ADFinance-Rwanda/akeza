# Multi-Tenant Task Platform

Spring Boot API + React UI for organizations, projects, and tasks. Identity is **Keycloak**. Cache and rate limits use **Redis**. Schema is **Flyway** on **PostgreSQL**.

The original assignment brief is in [ASSESSMENT.md](ASSESSMENT.md).

## Overview

A user signs in with Keycloak, creates or joins an organization, then manages projects and tasks. The server enforces tenant isolation and RBAC. Task creates are idempotent. Task updates require a `version` field (HTTP 409 on stale data).

## Features

- Keycloak OIDC login (`/api/me`)
- Organizations, memberships (`ORG_ADMIN` / `PROJECT_MANAGER` / `MEMBER`) plus platform `SUPER_ADMIN`
- Projects (status `ACTIVE`/`ARCHIVED`) and paginated/filterable tasks (`GET /api/tasks`)
- Projects and paginated/filterable tasks
- Optimistic locking on tasks
- `Idempotency-Key` on task create (wait-and-replay; 24h retention)
- Append-only audit log (ORG_ADMIN read; role changes audited)
- Redis Lua rate limits (429) and dashboard cache TTL
- Background worker: overdue scan + FAILED dead-letter inspect
- Organization suspend/reactivate
- Liveness: `GET /health` (also `/actuator/health/liveness`)
- Readiness: `GET /ready` (Postgres + Redis in prod; Compose healthcheck; 3s timeout → 503)
- Swagger UI

## Technology Stack

Java 17, Spring Boot 3.3.3, PostgreSQL 17, Keycloak 24, Redis 7, Flyway, React 18 + Vite, Docker Compose.

## Architecture

See `ARCHITECTURE.md`.

## Prerequisites

JDK 17, Docker Desktop (for the full stack), Node 20 if running the UI outside Docker.

## Database Setup

Compose creates the database. Without Docker:

```sql
CREATE DATABASE tenant_management;
```

Flyway runs `V1__init.sql` on startup.

## Environment Variables

Copy `.env.example` to `.env` for Compose. Local API without Compose: `application-local.properties.example` → `application-local.properties`.

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres |
| `KEYCLOAK_ISSUER` | JWT issuer, e.g. `http://localhost:8081/realms/taskmgr` |
| `KEYCLOAK_JWK_SET_URI` | JWKS URL reachable from the API container |
| `REDIS_HOST` / `REDIS_PORT` | Redis (Compose keeps Redis on the internal network; `docker compose exec redis redis-cli`) |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | Keycloak bootstrap admin |
| `RATE_LIMIT_REQUESTS` / `RATE_LIMIT_WINDOW_SECONDS` | Task write limiter (default 30 / 60s) |
| `ANALYTICS_CACHE_TTL_SECONDS` | Dashboard Redis TTL (default 60) |
| `IDEMPOTENCY_STALE_SECONDS` / `IDEMPOTENCY_RETENTION_HOURS` | In-progress reclaim / completed key purge |

## Local Installation

```powershell
cd multi-tenant-management
copy .env.example .env
```

## Running Without Docker

Start Postgres, Redis, and Keycloak yourself, then:

```powershell
.\mvnw.cmd spring-boot:run
cd frontend
npm install
npm run dev
```

UI: `http://localhost:5173` (proxies `/api` to `:8080`).

## Running With Docker

If you previously ran the old app+Postgres-only stack, reset the volume (this deletes data):

```powershell
docker compose down -v
docker compose up --build
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- UI: http://localhost:8088
- Keycloak: http://localhost:8081 — **local demo only** (`admin` / `admin` unless you override `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` in `.env`)
- Demo users (realm `taskmgr`, password `Password123`): `alice`, `bob`, `carol`, `dave`, `erin` (`SUPER_ADMIN`). These credentials exist so an evaluator can log in without extra setup. They are **not** production secrets; replace them and the Compose database fallbacks (`tenant` / `tenant`) before any shared or production host.

## Backup / restore

```powershell
.\scripts\backup.ps1
.\scripts\restore.ps1 -BackupFile .\backups\tenant_management-YYYYMMDD-HHMMSS.sql
```

```bash
./scripts/backup.sh
./scripts/restore.sh backups/tenant_management-YYYYMMDD-HHMMSS.sql
```

See `DISASTER_RECOVERY.md`.

## API Documentation

Authorize Swagger with a Keycloak access token. Tenant-scoped routes need header `X-Organization-Id`. Task create: `POST /api/tasks` (JSON `projectId`) or nested `POST /api/projects/{id}/tasks`, both with `Idempotency-Key`. Task get/update/delete: `/api/tasks/{id}` or nested `/api/projects/{id}/tasks/{taskId}`. Task PUT needs `version`.

Swagger UI (`/swagger-ui.html`) is public on this local Compose API so an assessor can browse schemas without a SPA session. Calling a protected operation still requires a valid JWT; Swagger does not bypass RBAC or tenant checks. Do not expose Swagger on a production internet deployment without authentication.

## Authentication

Keycloak realm `taskmgr`, client `task-web`. The API never issues JWTs.

## Testing

```powershell
.\mvnw.cmd clean verify
```

Tests use H2 + a mock Redis client by default. `RedisTtlIntegrationTest` starts embedded Redis. `PostgresRedisIntegrationTest` uses Testcontainers when a Docker engine is visible to the JVM. On this Windows Docker Desktop host the Java client hits the Desktop CLI npipe (`Status 400`) and the test is skipped; `docker compose` still works. They do not start Keycloak.

Frontend: `npm run build` (no unit test script).

## CI/CD

`.github/workflows/ci.yml` runs Maven verify, the frontend production build, and `docker compose build` (images are not pushed).

## Deployment

No hosted URL is provided. Deploy the Compose services or equivalent with real secrets.

## Project Structure

See `ARCHITECTURE.md`, `SECURITY.md`, `DATABASE.md`, `DISASTER_RECOVERY.md`, `AI_USAGE.md`, and `docs/adr/`.
