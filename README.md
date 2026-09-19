# Akeza — Multi-Tenant Project & Task Management Platform

A production-oriented **multi-tenant project and task management platform** built with **Spring Boot**, **React**, **PostgreSQL**, **Keycloak**, and **Redis**.

The platform supports organization-based tenant isolation, role-based access control, project and task management, optimistic concurrency, idempotent task creation, audit logging, rate limiting, background jobs, caching, health checks, and Docker-based deployment.

> **Assessment:** The original assignment brief is available in [ASSESSMENT.md](ASSESSMENT.md).

---

## Overview

A user authenticates through **Keycloak**, then creates or joins an organization and manages its projects and tasks.

The backend is responsible for enforcing:

* Tenant isolation
* Role-based access control
* Authentication and authorization
* Optimistic concurrency
* Idempotent task creation
* Audit logging
* Rate limiting
* Organization suspension
* Background job processing
* Redis caching

Tenant identity is derived and enforced server-side rather than being trusted from the frontend.

---

## Key Features

### Authentication & Authorization

* Keycloak OIDC/OAuth2 authentication
* JWT-based API authorization
* PKCE-based frontend login
* Platform-level `SUPER_ADMIN`
* Organization roles:

    * `ORG_ADMIN`
    * `PROJECT_MANAGER`
    * `MEMBER`
* Server-side RBAC enforcement
* Tenant isolation across organization-scoped resources

### Organizations

* Create and join organizations
* Organization membership management
* Organization suspension and reactivation
* Role changes with audit logging

### Projects & Tasks

* Project management
* Project statuses:

    * `ACTIVE`
    * `ARCHIVED`
* Task CRUD operations
* Paginated and filterable task listing
* Organization-scoped task access
* Optimistic concurrency using a task `version`
* Stale updates return HTTP `409 Conflict`

### Idempotency

Task creation supports the `Idempotency-Key` header.

* Prevents duplicate task creation when requests are retried
* Supports wait-and-replay behavior for concurrent requests
* Completed idempotency records are retained for 24 hours
* Stale in-progress requests can be reclaimed

### Audit Logging

* Append-only audit log
* Organization administrators can search and paginate audit records
* Role changes are audited
* Audit records are associated with the relevant organization and actor

### Redis

Redis is used for:

* API rate limiting
* Dashboard/analytics caching
* Idempotency coordination where applicable

Rate limiting uses Redis Lua scripts and returns HTTP `429 Too Many Requests` when limits are exceeded.

### Background Worker

The worker handles background maintenance tasks including:

* Overdue task scanning
* Failed/dead-letter job inspection
* Terminal job retention cleanup

Terminal jobs are cleaned up according to configurable retention periods. `PENDING` and `PROCESSING` jobs are never removed by terminal-job cleanup.

### Health & Readiness

* `GET /health` — liveness
* `GET /ready` — readiness
* `/actuator/health/liveness` — Spring Boot liveness endpoint

In production mode, readiness verifies required dependencies including PostgreSQL and Redis.

A failed readiness check returns HTTP `503 Service Unavailable`.

### API Documentation

Swagger/OpenAPI documentation is available through Swagger UI.

Protected API operations still require a valid Keycloak JWT and remain subject to RBAC and tenant-isolation checks.

---

## Technology Stack

| Component             | Technology                  |
| --------------------- | --------------------------- |
| Backend               | Java 17 + Spring Boot 3.3.3 |
| Frontend              | React 18 + Vite             |
| Database              | PostgreSQL 17               |
| Database Migrations   | Flyway                      |
| Authentication        | Keycloak 24                 |
| Cache & Rate Limiting | Redis 7                     |
| Containerization      | Docker + Docker Compose     |
| API Documentation     | OpenAPI / Swagger           |
| Testing               | JUnit, H2, Testcontainers   |
| Build                 | Maven + npm                 |

---

## Architecture

The application consists of the following major components:

```text
                         ┌──────────────────┐
                         │     React UI     │
                         │   Vite / PKCE    │
                         └────────┬─────────┘
                                  │
                                  │ HTTP / JWT
                                  ▼
                         ┌──────────────────┐
                         │  Spring Boot API │
                         │                  │
                         │ Auth / RBAC      │
                         │ Tenant Isolation │
                         │ Projects / Tasks │
                         │ Audit / Jobs     │
                         └──────┬─────┬─────┘
                                │     │
                  ┌─────────────┘     └─────────────┐
                  ▼                                 ▼
          ┌──────────────┐                  ┌──────────────┐
          │ PostgreSQL   │                  │    Redis     │
          │              │                  │              │
          │ Application  │                  │ Cache        │
          │ Data         │                  │ Rate Limits  │
          │ Audit Logs   │                  │ Coordination │
          └──────────────┘                  └──────────────┘

                         ┌──────────────────┐
                         │     Keycloak     │
                         │ OIDC / OAuth2    │
                         └──────────────────┘

                         ┌──────────────────┐
                         │ Background       │
                         │ Worker           │
                         │                  │
                         │ Jobs / Cleanup   │
                         └──────────────────┘
```

For the detailed architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Prerequisites

### Required

* JDK 17
* Docker Desktop
* Git

### Optional — Running the frontend outside Docker

* Node.js 20+

Docker Desktop is recommended because it provides the complete application stack, including:

* Spring Boot API
* React frontend
* PostgreSQL
* Redis
* Keycloak
* Background worker

---

## Installation

Clone the repository and enter the project directory:

```powershell
git clone <repository-url>
cd multi-tenant-management
```

Create the local environment file:

```powershell
copy .env.example .env
```

Review `.env` before starting the application.

---

# Running with Docker

Docker Compose is the recommended way to run the complete system.

### Start the stack

```powershell
docker compose up --build
```

### If you need a clean database

> ⚠️ This deletes the PostgreSQL and Keycloak Docker volumes and therefore removes local application data.

```powershell
docker compose down -v
docker compose up --build
```

### Services

| Service         | URL                                   |
| --------------- | ------------------------------------- |
| React UI        | http://localhost:8088                 |
| Spring Boot API | http://localhost:8080                 |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| Keycloak        | http://localhost:8081                 |
| PostgreSQL      | localhost:5432                        |
| Redis           | localhost:6379                        |

Check service status with:

```powershell
docker compose ps
```

---

## Demo Credentials

The Docker Compose environment includes demo credentials for assessment purposes.

### Keycloak Admin

```text
Username: admin
Password: admin
```

These values can be overridden through:

```text
KEYCLOAK_ADMIN
KEYCLOAK_ADMIN_PASSWORD
```

### Demo Users

Realm:

```text
taskmgr
```

Password:

```text
Password123
```

Available demo users:

```text
alice
bob
carol
dave
erin
```

`erin` is configured as the platform `SUPER_ADMIN`.

> ⚠️ These credentials are **demo/assessment configuration only**. They must not be used on a shared or production deployment. Replace the credentials and database defaults before deployment.

---

# Running Without Docker

When running the API outside Docker, PostgreSQL, Redis, and Keycloak must already be running.

Start the Spring Boot API:

```powershell
.\mvnw.cmd spring-boot:run
```

Then start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

The frontend will be available at:

```text
http://localhost:5173
```

The Vite development server proxies `/api` requests to the backend on port `8080`.

---

## Database

With Docker Compose, PostgreSQL is created automatically.

Without Docker, create the database manually:

```sql
CREATE DATABASE tenant_management;
```

Flyway automatically applies database migrations when the application starts.

Current migrations are located under:

```text
src/main/resources/db/migration/
```

---

# Environment Variables

Copy `.env.example` to `.env` and configure the environment as required.

| Variable                      | Purpose                                                        |
| ----------------------------- | -------------------------------------------------------------- |
| `DATABASE_URL`                | PostgreSQL JDBC/database URL                                   |
| `DATABASE_USERNAME`           | PostgreSQL username                                            |
| `DATABASE_PASSWORD`           | PostgreSQL password                                            |
| `KEYCLOAK_ISSUER`             | Keycloak JWT issuer                                            |
| `KEYCLOAK_JWK_SET_URI`        | JWKS endpoint reachable by the API                             |
| `REDIS_HOST`                  | Redis hostname                                                 |
| `REDIS_PORT`                  | Redis port                                                     |
| `KEYCLOAK_ADMIN`              | Keycloak bootstrap administrator                               |
| `KEYCLOAK_ADMIN_PASSWORD`     | Keycloak bootstrap administrator password                      |
| `RATE_LIMIT_REQUESTS`         | Maximum requests allowed within the rate-limit window          |
| `RATE_LIMIT_WINDOW_SECONDS`   | Rate-limit window duration                                     |
| `ANALYTICS_CACHE_TTL_SECONDS` | Dashboard cache TTL                                            |
| `IDEMPOTENCY_STALE_SECONDS`   | Time before an incomplete idempotency request can be reclaimed |
| `IDEMPOTENCY_RETENTION_HOURS` | Completed idempotency-key retention period                     |
| `JOB_DONE_RETENTION_HOURS`    | Retention period for completed jobs                            |
| `JOB_FAILED_RETENTION_HOURS`  | Retention period for failed jobs                               |

Default retention configuration:

```text
DONE jobs:    24 hours
FAILED jobs:  168 hours
```

`PENDING` and `PROCESSING` jobs are excluded from terminal-job cleanup.

---

# Authentication

Authentication is handled by **Keycloak**.

The configured realm is:

```text
taskmgr
```

The frontend uses the public `task-web` client with PKCE.

The API does **not** issue JWT access tokens. It validates tokens issued by Keycloak.

## Development Keycloak Configuration

Docker Compose starts Keycloak using development mode over HTTP.

The following are therefore development/assessment settings:

* HTTP instead of HTTPS
* Demo users
* `admin` / `admin`
* `Password123`

These settings are **not suitable for production**.

A production deployment should use:

* HTTPS
* Production Keycloak startup configuration
* Strong unique credentials
* Secure secret management
* Appropriate realm/client configuration

---

## Custom Login Theme

A custom `akeza` Keycloak login theme is included at:

```text
keycloak/themes/akeza
```

The realm configuration sets:

```text
loginTheme=akeza
```

The realm JSON is imported when the realm is initially created.

If the `keycloak_data` volume already contains an imported realm, the existing realm will not automatically receive later theme configuration changes.

To apply the theme manually:

```powershell
docker compose exec keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin

docker compose exec keycloak /opt/keycloak/bin/kcadm.sh update realms/taskmgr -s loginTheme=akeza
```

---

# API Usage

## Tenant Context

Organization-scoped endpoints require:

```http
X-Organization-Id: <organization-id>
```

The backend validates that the authenticated user has access to the requested organization.

The frontend cannot grant itself access to another tenant simply by changing the organization ID.

---

## Tasks

Tasks can be created using either endpoint:

```http
POST /api/tasks
```

or:

```http
POST /api/projects/{projectId}/tasks
```

Both require an `Idempotency-Key`.

Example:

```http
Idempotency-Key: 8c7f0c6d-7b32-4d19-9d9c-example
```

Task retrieval, update, and deletion are available through:

```http
/api/tasks/{id}
```

and the nested project routes where applicable.

### Optimistic Concurrency

Task updates require the current `version` value.

If another request has already modified the task, the submitted version becomes stale and the API returns:

```text
409 Conflict
```

This prevents silent overwriting of concurrent changes.

---

# Swagger / OpenAPI

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

To test protected operations:

1. Obtain a Keycloak access token.
2. Select **Authorize** in Swagger UI.
3. Provide the token.
4. Include `X-Organization-Id` for tenant-scoped endpoints.
5. Execute the requested API operation.

Swagger UI itself is publicly accessible in the local Compose environment so an evaluator can inspect the API schemas without first authenticating through the SPA.

> Protected API operations still enforce JWT authentication, RBAC, and tenant isolation. Swagger does not bypass security.

For production deployments, Swagger should not be publicly exposed without appropriate access controls.

---

# Backup & Restore

PowerShell:

```powershell
.\scripts\backup.ps1
```

Restore:

```powershell
.\scripts\restore.ps1 -BackupFile .\backups\tenant_management-YYYYMMDD-HHMMSS.sql
```

Linux/macOS/Git Bash:

```bash
./scripts/backup.sh
```

Restore:

```bash
./scripts/restore.sh backups/tenant_management-YYYYMMDD-HHMMSS.sql
```

See [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md) for backup, restore, and recovery procedures.

---

# Testing

Run the complete backend verification suite:

```powershell
.\mvnw.cmd clean verify
```

The test suite includes coverage for areas such as:

* Authentication
* Tenant isolation
* RBAC
* Organization access
* Task CRUD
* Optimistic concurrency
* Idempotency
* Redis behavior
* Integration scenarios

### Test Infrastructure

Most tests use:

* H2 for database tests
* Mock Redis behavior

`RedisTtlIntegrationTest` uses embedded Redis.

`PostgresRedisIntegrationTest` uses Testcontainers when a Docker engine is available to the JVM.

Keycloak is not started automatically by the test suite.

### Frontend

Build the production frontend with:

```powershell
cd frontend
npm run build
```

There is currently no frontend unit-test script configured.

---

# CI/CD

GitHub Actions is configured in:

```text
.github/workflows/ci.yml
```

The pipeline performs:

1. Maven verification
2. Frontend production build
3. Docker Compose image build

The CI workflow currently builds the images but does not push them to a container registry.

---

# Project Structure

```text
multi-tenant-management/
│
├── src/
│   ├── main/
│   │   ├── java/                  # Spring Boot application
│   │   └── resources/
│   │       └── db/migration/      # Flyway migrations
│   │
│   └── test/                      # Backend tests
│
├── frontend/
│   └── src/                       # React application
│
├── keycloak/
│   ├── realm-taskmgr.json         # Realm configuration
│   └── themes/akeza/              # Custom login theme
│
├── scripts/
│   ├── backup.ps1
│   ├── restore.ps1
│   ├── backup.sh
│   └── restore.sh
│
├── docs/
│   └── adr/                       # Architecture Decision Records
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── ARCHITECTURE.md
├── SECURITY.md
├── DATABASE.md
├── DISASTER_RECOVERY.md
├── AI_USAGE.md
├── ASSESSMENT.md
├── docker-compose.yml
└── pom.xml
```

---

# Documentation

Additional project documentation:

* [Assessment](ASSESSMENT.md)
* [Architecture](ARCHITECTURE.md)
* [Security](SECURITY.md)
* [Database](DATABASE.md)
* [Disaster Recovery](DISASTER_RECOVERY.md)
* [AI Usage](AI_USAGE.md)
* [Architecture Decision Records](docs/adr/)

---

# Deployment

No hosted production URL is currently provided.

The application can be deployed using Docker Compose or equivalent infrastructure.

For production deployment:

* Replace all demo credentials
* Use strong secrets
* Enable HTTPS
* Use production Keycloak configuration
* Protect Swagger/OpenAPI endpoints
* Use managed or properly secured PostgreSQL and Redis
* Configure persistent storage and backups
* Review [SECURITY.md](SECURITY.md)
* Review [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md)

---

## License

This project was developed as a software engineering assessment project.
