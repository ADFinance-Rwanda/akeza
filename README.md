# Multi-Tenant Management API

Spring Boot backend for managing organizations (tenants), users, and tenant memberships with JWT authentication and role-based access control.

## Overview

Authenticated users can register, create tenants, and manage who belongs to each tenant. Access is isolated: a user only sees tenants they belong to, and membership roles (`OWNER`, `ADMIN`, `MEMBER`) control who can change tenant data or memberships.

This is a REST API only. There is no frontend in this project.

## Features

- User registration and login with JWT Bearer tokens
- Passwords stored with BCrypt (never returned in API responses)
- Tenant CRUD (create, list, get, update, delete)
- User profile CRUD, scoped to the current user and people who share a tenant
- Membership management (add, list, change role, remove)
- Role checks: owner/admin manage members; only owners update or delete a tenant
- Last-owner protection (cannot remove, demote, or delete the only owner)
- Duplicate email and tenant slug rejected with HTTP 409
- Consistent JSON error responses (400, 401, 403, 404, 409, 500)
- OpenAPI/Swagger UI for exploring and calling the API
- Health endpoint at `/actuator/health`
- Docker Compose stack (application + PostgreSQL)
- GitHub Actions CI (`mvn clean verify`)

## Technology Stack

- Java 17
- Spring Boot 3.3.3
- Spring Web, Spring Data JPA, Spring Validation, Spring Security, Spring Boot Actuator
- PostgreSQL 17 (runtime and Docker)
- H2 (tests only)
- JJWT 0.12.6 (HS256)
- springdoc-openapi 2.6.0
- Lombok
- Maven Wrapper
- Docker / Docker Compose

## Architecture

```text
Controller  →  Service  →  Repository  →  PostgreSQL
```

Business rules live in services (`AuthService`, `TenantService`, `UserService`, `MembershipService`, `AccessService`). Controllers validate HTTP input and return DTOs. JPA entities are not exposed as API payloads.

## Prerequisites

- JDK 17+
- Maven 3.9+ (or use `mvnw` / `mvnw.cmd` in this repo)
- PostgreSQL 12+ if running without Docker (developed against PostgreSQL 17)
- Docker Desktop if running with Docker Compose

## Database Setup

Create an empty database when running without Docker:

```sql
CREATE DATABASE tenant_management;
```

Tables (`tenants`, `users`, `memberships`) are created or updated automatically on startup (`spring.jpa.hibernate.ddl-auto=update`).

Relationships:

- A tenant has many memberships
- A user has many memberships
- A membership belongs to one tenant and one user (`OWNER` | `ADMIN` | `MEMBER`)
- Unique: `tenants.slug`, `users.email`, (`memberships.tenant_id`, `memberships.user_id`)
- Index: `memberships.user_id` (membership lookup by user)

If PostgreSQL 10 already occupies port `5432` on Windows, run PostgreSQL 17 on another port (this project used `5433` locally) and set `DATABASE_URL` accordingly.

Docker Compose starts its own PostgreSQL and does not use the host database.

## Environment Variables

Copy the example file and edit values. Do not commit real passwords.

```text
application-local.properties.example  →  application-local.properties
```

For Docker Compose, copy `.env.example` to `.env` if you want to override the local defaults.

`application-local.properties` and `.env` are gitignored.

| Variable | Required locally | Docker default | Purpose |
| --- | --- | --- | --- |
| `DATABASE_URL` | No (`jdbc:postgresql://localhost:5432/tenant_management`) | `jdbc:postgresql://db:5432/tenant_management` | JDBC URL |
| `DATABASE_USERNAME` | No (`postgres`) | `tenant` | Database user |
| `DATABASE_PASSWORD` | **Yes** (no default) | `tenant` (local Compose only) | Database password |
| `JWT_SECRET` | **Yes** (no default, ≥ 32 characters) | local Compose placeholder | HMAC signing secret |
| `JWT_EXPIRATION_MS` | No | `86400000` (24h) | Access token lifetime |
| `SERVER_PORT` | No | `8080` | Host HTTP port |

Production deployments must set `DATABASE_PASSWORD` and `JWT_SECRET` to strong unique values. Do not use the Compose placeholders outside local development.

## Local Installation

```powershell
git clone <repository-url>
cd multi-tenant-management
copy application-local.properties.example application-local.properties
```

Edit `application-local.properties` with your PostgreSQL password and a long JWT secret.

## Running Without Docker

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`.

Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## Running With Docker

Docker Desktop must be running.

```powershell
copy .env.example .env
docker compose build
docker compose up
```

Compose starts PostgreSQL and the Spring Boot app on the `tenant-net` network. The database port is not published on the host; the API is at `http://localhost:8080`.

Stop:

```powershell
docker compose down
```

Data is kept in the `postgres_data` volume. Add `-v` to `docker compose down` only if you want to delete that volume.

## API Documentation

After the application is running:

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Register or log in first, then click **Authorize** in Swagger and paste the JWT (without the `Bearer ` prefix).

Import `postman/Multi-Tenant-Management.postman_collection.json` for the same flows. Register and Login save `{{token}}` for later requests.

## Authentication

Public:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /actuator/health`
- Swagger / OpenAPI

All other `/api/**` routes require:

```http
Authorization: Bearer <accessToken>
```

Tenant roles (stored on memberships, not on the JWT):

| Action | OWNER | ADMIN | MEMBER |
| --- | --- | --- | --- |
| View tenant and members | yes | yes | yes |
| Add/update/remove members | yes | yes (cannot assign or manage OWNER) | no |
| Update or delete tenant | yes | no | no |

Users can update or delete only their own account. Listing users returns the current user plus users who share at least one tenant.

## Testing

Tests use an in-memory H2 database and do not need PostgreSQL or Docker.

```powershell
.\mvnw.cmd test
```

Full build (compile + tests):

```powershell
.\mvnw.cmd clean verify
```

## CI/CD

GitHub Actions workflow: `.github/workflows/ci.yml`

On push or pull request to `main`/`master` it:

1. Checks out the repository
2. Sets up Temurin JDK 17
3. Caches Maven dependencies
4. Runs `./mvnw -B clean verify`

The workflow fails if tests fail. There is no automated production deploy in this repository.

## Deployment

This assessment backend is packaged as a Docker image (`Dockerfile`) and can be deployed to any host that can run the image and a PostgreSQL instance.

Required production environment variables:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`

Set `SPRING_PROFILES_ACTIVE=prod` in production. Do not commit production secrets.

A live hosted URL is not included in this repository. Deploy from the Docker image to the platform required by your environment.

## Project Structure

```text
multi-tenant-management/
├── pom.xml
├── mvnw / mvnw.cmd
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── application-local.properties.example
├── .github/workflows/ci.yml
├── postman/Multi-Tenant-Management.postman_collection.json
└── src/
    ├── main/java/com/tenant/management/
    │   ├── TenantManagementApplication.java
    │   ├── config/          # Security + OpenAPI
    │   ├── controller/      # Auth, Tenant, User
    │   ├── dto/
    │   ├── entity/
    │   ├── exception/
    │   ├── repository/
    │   ├── security/        # JWT filter, JwtService, UserPrincipal
    │   └── service/
    ├── main/resources/
    │   ├── application.properties
    │   └── application-prod.properties
    └── test/java/com/tenant/management/
```
