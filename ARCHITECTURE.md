# Architecture

The platform is a multi-tenant task manager.

```text
Browser (React)
    → Keycloak (OIDC login)
    → Backend Spring Boot (JWT resource server)
         → PostgreSQL
         → Redis (cache + rate limit)
         → jobs table consumed by worker
```

- Identity comes from Keycloak. The API does not issue passwords or JWTs.
- After login, `/api/me` syncs the Keycloak `sub` into `users`.
- Tenant scope is the `X-Organization-Id` header, checked against `memberships` (`ORG_ADMIN`, `PROJECT_MANAGER`, `MEMBER`). Platform `SUPER_ADMIN` (Keycloak realm role, stored on `users.super_admin`) may act in any organization with that header, including `SUSPENDED` organizations.
- Projects have `ACTIVE`/`ARCHIVED` status. Tasks have `due_date`, priorities `LOW`/`MEDIUM`/`HIGH`/`CRITICAL`, and statuses `TODO`/`IN_PROGRESS`/`DONE`.
- `GET /api/tasks` lists the current tenant's tasks (paginated, filtered). Nested `/api/projects/{id}/tasks` remains for project-scoped lists. Flat aliases `POST /api/tasks` (body `projectId`), `GET|PUT|DELETE /api/tasks/{id}` call the same `TaskService` and the same org/RBAC checks.
- Projects and tasks always store `organization_id` and queries filter by it.
- Task writes evict the analytics cache synchronously and also enqueue `INVALIDATE_ANALYTICS`. The worker additionally runs `SCAN_OVERDUE` (audit `TASK_OVERDUE` once per overdue task). Failed jobs after max attempts are `FAILED` with `dead_lettered_at` (`docs/adr/0010-overdue-worker-dlq.md`).

See `docs/adr/` (0001–0010).
