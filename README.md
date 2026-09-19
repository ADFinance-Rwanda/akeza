# Akeza — Multi-Tenant Project & Task Management Platform

A production-oriented **multi-tenant project and task management platform** built with **Java 17, Spring Boot, React, PostgreSQL, Keycloak, Redis, and Docker Compose**.

The platform provides organization-based tenant isolation, role-based access control, project and task management, optimistic concurrency control, idempotent task creation, audit logging, Redis-backed rate limiting and caching, background processing, health/readiness checks, and automated testing.

> **Assessment brief:** [ASSESSMENT.md](ASSESSMENT.md)

---

# 1. System Overview

Akeza follows a multi-tenant architecture where users belong to organizations and access to resources is enforced on the server.

```text
                         ┌─────────────────┐
                         │    React UI     │
                         │   Vite + PKCE   │
                         └────────┬────────┘
                                  │
                              HTTP + JWT
                                  │
                                  ▼
                         ┌─────────────────┐
                         │  Spring Boot    │
                         │      API        │
                         │                 │
                         │ Authentication │
                         │ RBAC            │
                         │ Tenant Isolation│
                         │ Business Logic  │
                         └───────┬─┬───────┘
                                 │ │
                   ┌─────────────┘ └──────────────┐
                   ▼                              ▼
           ┌───────────────┐              ┌───────────────┐
           │  PostgreSQL   │              │     Redis     │
           │               │              │               │
           │ Application   │              │ Cache         │
           │ Data          │              │ Rate Limits   │
           │ Audit Logs    │              │ Idempotency   │
           └───────────────┘              └───────────────┘

                         ┌─────────────────┐
                         │    Keycloak     │
                         │   OIDC / OAuth2 │
                         └─────────────────┘

                         ┌─────────────────┐
                         │ Background      │
                         │ Worker          │
                         └─────────────────┘
```

Detailed architecture: [ARCHITECTURE.md](ARCHITECTURE.md)

---

# 2. Key Features

## Authentication

* Keycloak OIDC/OAuth2 authentication
* JWT-based API authorization
* PKCE frontend authentication
* API does not issue JWTs
* Server-side authentication and authorization

## Multi-Tenancy

* Organizations act as tenants
* Organization membership controls access
* Organization-scoped resources
* Server-side tenant isolation
* Client-provided organization IDs are validated against the authenticated user's membership

## RBAC

Platform role:

* `SUPER_ADMIN`

Organization roles:

* `ORG_ADMIN`
* `PROJECT_MANAGER`
* `MEMBER`

Permissions are enforced by the backend.

## Projects & Tasks

* Project CRUD
* Project statuses: `ACTIVE`, `ARCHIVED`
* Task CRUD
* Pagination and filtering
* Organization-scoped task access
* Optimistic concurrency control
* HTTP `409 Conflict` for stale task versions

## Idempotency

Task creation supports:

```http
Idempotency-Key: <unique-key>
```

Duplicate retries using the same key replay the original result rather than creating duplicate tasks.

## Audit Logging

* Append-only audit log
* Organization administrators can search and paginate audit records
* Role changes are audited
* Audit records include the relevant actor and organization context

## Redis

Redis is used for:

* Dashboard/analytics caching
* Rate limiting
* Idempotency coordination

## Background Worker

The worker performs background maintenance such as:

* Overdue task scanning
* Failed/dead-letter job inspection
* Terminal job cleanup

## Health & Readiness

* `/health` — liveness
* `/ready` — readiness
* `/actuator/health/liveness` — Spring Boot liveness

---

# 3. Technology Stack

| Component             | Technology                 |
| --------------------- | -------------------------- |
| Language              | Java 17                    |
| Backend               | Spring Boot 3.3.3          |
| Security              | Spring Security + Keycloak |
| Authentication        | OIDC / OAuth2              |
| Frontend              | React 18                   |
| Frontend Tooling      | Vite                       |
| Database              | PostgreSQL 17              |
| Migrations            | Flyway                     |
| Cache / Rate Limiting | Redis 7                    |
| API Documentation     | OpenAPI / Swagger          |
| Testing               | JUnit, H2, Testcontainers  |
| Build                 | Maven                      |
| Containers            | Docker                     |
| Orchestration         | Docker Compose             |
| CI                    | GitHub Actions             |

---

# 4. Prerequisites

Install:

* Git
* JDK 17
* Docker Desktop

Node.js 20+ is only required if running the React frontend outside Docker.

Docker Desktop is the recommended way to run the complete system.

---

# 5. Quick Start

## Clone the repository

```powershell
git clone <repository-url>
cd multi-tenant-management
```

## Create the environment file

```powershell
copy .env.example .env
```

Review `.env` if you need to change credentials or configuration.

## Start the complete system

```powershell
docker compose up --build
```

Wait until the services are healthy.

Check:

```powershell
docker compose ps
```

The expected services are:

```text
app
frontend
postgres
redis
keycloak
worker
```

---

# 6. Service URLs

| Service         | URL                                   |
| --------------- | ------------------------------------- |
| React UI        | http://localhost:8088                 |
| Spring Boot API | http://localhost:8080                 |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| Keycloak        | http://localhost:8081                 |

PostgreSQL and Redis are also exposed locally:

```text
PostgreSQL: localhost:5432
Redis:      localhost:6379
```

---

# 7. Demo Users

The Compose environment includes preconfigured Keycloak users so an evaluator can immediately test the application.

## Realm

```text
taskmgr
```

## Password

```text
Password123
```

## Users

| Username | Role              |
| -------- | ----------------- |
| `alice`  | Organization user |
| `bob`    | Organization user |
| `carol`  | Organization user |
| `dave`   | Organization user |
| `erin`   | `SUPER_ADMIN`     |

The exact organization memberships and roles are defined by the imported Keycloak/application seed configuration.

## Keycloak Admin

```text
Username: admin
Password: admin
```

These credentials are for the local assessment environment only.

> **Security note:** Do not use these credentials on a production or shared deployment. Replace all demo credentials and database defaults before deployment.

---

# 8. How to Log In

## Option A — React UI

Open:

```text
http://localhost:8088
```

Click **Login**.

The application redirects to Keycloak.

Use one of the demo accounts:

```text
Username: alice
Password: Password123
```

After successful authentication, Keycloak redirects back to the React application.

The frontend receives the authenticated session/token and uses it when calling the API.

---

## Authentication Flow

```text
Browser
   │
   │ Login
   ▼
React
   │
   │ OIDC / PKCE
   ▼
Keycloak
   │
   │ Access Token
   ▼
React
   │
   │ Authorization: Bearer <JWT>
   ▼
Spring Boot API
   │
   ├── Validate JWT
   ├── Identify user
   ├── Check organization membership
   └── Check RBAC permissions
```

The API does not directly handle user passwords.

---

# 9. How to Demonstrate Tenant Isolation

Tenant isolation is enforced by the backend.

The important security rule is:

> A user cannot access another organization's resources simply by changing the organization ID.

## Demonstration

### Step 1 — Login as a user from Organization A

For example:

```text
alice
Password123
```

Open the application and identify the organization available to Alice.

### Step 2 — Access an organization-scoped resource

Use the application's organization context or Swagger.

For example:

```http
GET /api/tasks
X-Organization-Id: <organization-A-id>
Authorization: Bearer <alice-token>
```

The request succeeds because Alice belongs to that organization.

### Step 3 — Try another organization

Change only:

```http
X-Organization-Id: <organization-B-id>
```

while keeping Alice's JWT.

The backend verifies Alice's membership against the requested organization.

The request is rejected because Alice does not belong to Organization B.

### What this demonstrates

```text
JWT identity
     ↓
User
     ↓
Membership
     ↓
Requested organization
     ↓
Resource
```

The frontend cannot grant itself access to another tenant.

Tenant isolation is therefore enforced at the API/data-access layer rather than being trusted from the UI.

---

# 10. How to Demonstrate RBAC

RBAC controls what an authenticated user is allowed to do.

The system has:

```text
SUPER_ADMIN
ORG_ADMIN
PROJECT_MANAGER
MEMBER
```

## Demonstration

### Organization administrator

Log in as a user configured with:

```text
ORG_ADMIN
```

Demonstrate an organization-management operation such as:

* Managing organization members
* Changing member roles
* Viewing/searching organization audit logs

The operation should succeed when the user has the required role.

### Member

Log in as a user configured with:

```text
MEMBER
```

Attempt an organization-administration operation.

The backend should reject the request because the user does not have the required role.

### Super administrator

Log in as:

```text
erin
Password123
```

`erin` is configured as the platform-level:

```text
SUPER_ADMIN
```

This demonstrates the distinction between platform-level administration and organization-level roles.

### What this demonstrates

RBAC is enforced by the backend:

```text
Authenticated User
        ↓
       Role
        ↓
Required Permission
        ↓
Allow / Deny
```

The frontend hiding a button is not considered a security control. The API performs the actual authorization check.

---

# 11. How to Demonstrate 409 Optimistic Concurrency

Tasks use a `version` field to prevent stale updates from overwriting newer changes.

## Demonstration

Use the API or Swagger.

### Step 1 — Read a task

```http
GET /api/tasks/{taskId}
```

Suppose the response contains:

```json
{
  "id": 10,
  "title": "Example task",
  "version": 5
}
```

The current version is:

```text
5
```

### Step 2 — Update the task

Send an update using:

```json
{
  "title": "First update",
  "version": 5
}
```

The update succeeds and the task version becomes:

```text
6
```

### Step 3 — Reuse the stale version

Send another update using:

```json
{
  "title": "Stale update",
  "version": 5
}
```

The server detects that version `5` is no longer current.

Expected response:

```text
HTTP 409 Conflict
```

### What this demonstrates

```text
Client A reads version 5
Client B updates → version 6
Client A updates using version 5
             ↓
       409 Conflict
```

This prevents silent lost updates.

---

# 12. How to Demonstrate Idempotency

Task creation supports an `Idempotency-Key`.

## Demonstration

Send:

```http
POST /api/tasks
Idempotency-Key: demo-task-001
```

with a valid task payload.

The server creates the task.

Now send the **same request again** with the same:

```http
Idempotency-Key: demo-task-001
```

The server recognizes that the operation has already been processed.

The second request replays the original result instead of creating another task.

### Verify the behavior

Before:

```text
Tasks = 10
```

First request:

```text
Tasks = 11
```

Retry with the same idempotency key:

```text
Tasks = 11
```

A duplicate task is not created.

### Why this matters

This protects against retries caused by:

* Network failures
* Client timeouts
* Browser retries
* Distributed-system retry behavior

---

# 13. How to Demonstrate Rate Limiting

The task-write endpoints are protected by Redis-backed rate limiting.

The default configuration is:

```text
30 requests / 60 seconds
```

Configuration can be changed using:

```text
RATE_LIMIT_REQUESTS
RATE_LIMIT_WINDOW_SECONDS
```

## Demonstration

### Option 1 — Use Swagger

Open:

```text
http://localhost:8080/swagger-ui.html
```

Authorize using a valid Keycloak access token.

Repeatedly call a rate-limited task write endpoint within the configured window.

Once the limit is exceeded, the API returns:

```text
HTTP 429 Too Many Requests
```

### Option 2 — Temporarily lower the limit

For a quick demonstration, configure:

```text
RATE_LIMIT_REQUESTS=3
RATE_LIMIT_WINDOW_SECONDS=60
```

Restart the stack:

```powershell
docker compose up --build
```

Then perform several requests against the protected endpoint.

Expected behavior:

```text
Request 1 → allowed
Request 2 → allowed
Request 3 → allowed
Request 4 → 429 Too Many Requests
```

Redis stores the rate-limit state, allowing the counter to be shared across application instances.

---

# 14. How to Check Health & Readiness

The application exposes separate liveness and readiness checks.

## Liveness

Open:

```text
http://localhost:8080/health
```

Expected result:

```text
UP
```

Spring Boot's liveness endpoint is also available at:

```text
http://localhost:8080/actuator/health/liveness
```

Liveness answers:

> Is the application process alive?

---

## Readiness

Open:

```text
http://localhost:8080/ready
```

Readiness checks whether required infrastructure is available.

In production configuration this includes:

```text
Spring Boot API
      │
      ├── PostgreSQL ✓
      │
      └── Redis ✓
```

If the application is ready:

```text
HTTP 200
```

If required dependencies are unavailable:

```text
HTTP 503 Service Unavailable
```

Readiness therefore answers:

> Can this application currently serve normal requests?

---

# 15. Database

PostgreSQL stores the application's persistent data.

The main relational concepts include:

```text
Organizations
Members
Projects
Tasks
Audit Logs
Jobs
Idempotency Records
```

The database is initialized and upgraded using Flyway migrations.

Create a database manually when running outside Docker:

```sql
CREATE DATABASE tenant_management;
```

Flyway automatically applies migrations when the application starts.

Migration files are located under:

```text
src/main/resources/db/migration/
```

---

# 16. Redis

Redis provides fast in-memory storage for temporary and frequently accessed data.

Akeza uses Redis for:

### Caching

Dashboard/analytics responses can be cached to reduce repeated database work.

### Rate limiting

Redis maintains request counters for rate-limited operations.

### Idempotency

Redis participates in coordination for idempotent operations.

Redis Lua scripts are used where atomic rate-limit operations are required.

---

# 17. Keycloak

Keycloak is the application's identity provider.

The configured realm is:

```text
taskmgr
```

The frontend uses the public:

```text
task-web
```

client with PKCE.

The API validates Keycloak-issued JWTs but does not issue tokens itself.

For local assessment purposes, Keycloak runs in development mode over HTTP.

Production deployments should use:

* HTTPS
* Production Keycloak configuration
* Strong unique secrets
* Secure secret management
* Production-grade identity configuration

---

# 18. API Documentation

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

To test protected endpoints:

1. Log in through Keycloak.
2. Obtain a valid access token.
3. Open Swagger UI.
4. Select **Authorize**.
5. Provide the token.
6. Add `X-Organization-Id` to tenant-scoped requests.
7. Execute the endpoint.

Swagger being publicly accessible does **not** disable API security.

Protected endpoints still enforce:

* JWT validation
* RBAC
* Tenant isolation
* Resource authorization

---

# 19. Backup & Restore

### PowerShell

Backup:

```powershell
.\scripts\backup.ps1
```

Restore:

```powershell
.\scripts\restore.ps1 -BackupFile .\backups\tenant_management-YYYYMMDD-HHMMSS.sql
```

### Bash

Backup:

```bash
./scripts/backup.sh
```

Restore:

```bash
./scripts/restore.sh backups/tenant_management-YYYYMMDD-HHMMSS.sql
```

See [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md) for the complete recovery procedure.

---

# 20. Testing

Run the backend test suite:

```powershell
.\mvnw.cmd clean verify
```

Tests cover areas including:

* Authentication
* Authorization
* Tenant isolation
* RBAC
* Organization access
* Task CRUD
* Optimistic concurrency
* Idempotency
* Redis behavior
* Integration scenarios

### Test infrastructure

Most unit/integration tests use:

* H2
* Mock Redis

`RedisTtlIntegrationTest` uses embedded Redis.

`PostgresRedisIntegrationTest` uses Testcontainers when Docker is available to the JVM.

Keycloak is not automatically started by the test suite.

### Frontend build

```powershell
cd frontend
npm install
npm run build
```

---

# 21. CI/CD

GitHub Actions is configured in:

```text
.github/workflows/ci.yml
```

The CI pipeline performs:

```text
Maven verify
     ↓
Frontend production build
     ↓
Docker Compose build
```

The workflow currently builds the images but does not push them to a container registry.

---

# 22. Project Structure

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
│   ├── realm-taskmgr.json         # Keycloak realm configuration
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

# 23. Additional Documentation

* [Assessment](ASSESSMENT.md)
* [Architecture](ARCHITECTURE.md)
* [Security](SECURITY.md)
* [Database](DATABASE.md)
* [Disaster Recovery](DISASTER_RECOVERY.md)
* [AI Usage](AI_USAGE.md)
* [Architecture Decision Records](docs/adr/)

---

# 24. Production Considerations

The included Docker Compose environment is designed for local assessment and demonstration.

Before deploying to a shared or production environment:

* Replace all demo credentials
* Replace database default credentials
* Enable HTTPS
* Use production Keycloak configuration
* Secure Swagger/OpenAPI access
* Use proper secret management
* Configure persistent PostgreSQL storage
* Configure Redis appropriately
* Configure production backups
* Review [SECURITY.md](SECURITY.md)
* Review [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md)

---


This provides a quick end-to-end demonstration of the platform's core security, reliability, and infrastructure requirements.
