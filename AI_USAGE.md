# AI Usage

Cursor Grok 4.6 was used to implement, audit, and prepare this assessment project.

## Where AI assistance was used

- Layered Spring Boot services, Flyway schema, Keycloak resource-server wiring
- Redis cache/rate-limit, Postgres-backed worker, React + keycloak-js UI
- Docker Compose, CI workflow, backup/restore scripts, ADRs, and assessment documentation
- Test fixtures (RSA-signed JWTs, mock Redis) and security/integration tests
- Final hardening: MEMBER cannot delete tasks, unknown jobs are dead-lettered (never DONE), job retention, server-side audit search, project `taskCount` aggregate, frontend workspace/View/Unassigned/CSP fixes.

## Real prompts used (Phase 1–2)

1. Full assessment audit of the repository against the spec (no code changes). Used to produce the original gap list.
2. Phase 1 remediation: domain model, RBAC, `/api/tasks`, dashboard, Redis TTL, health/404.
3. Phase 2 production hardening: idempotency wait-and-replay, audit/role change, Lua rate limit, org suspension, overdue worker/DLQ, Testcontainers, Docker/CI.

## What was accepted

- PostgreSQL unique idempotency with wait/replay and stale reclaim (not Redis-only keys).
- Lua `INCR`+`EXPIRE` for rate limits; writes remain fail-closed.
- `SCAN_OVERDUE` as the meaningful worker job; `FAILED` + `dead_lettered_at` as DLQ.
- Central suspension check in `AccessService`; SUPER_ADMIN bypass.

## AI-generated code that was rejected or changed

- Immediate `409 in progress` for concurrent idempotency was replaced with wait-and-replay after live/assessment requirements.
- Testcontainers-only Redis TTL tests were previously replaced with embedded Redis because Maven had no Docker; Phase 2 adds Testcontainers **in addition**, skipped when Docker is unavailable.
- Partial SQL index `WHERE overdue_notified_at IS NULL` was rejected because Flyway tests run on H2.

## Human verification

- `.\mvnw.cmd -B test` including H2, embedded Redis, and Testcontainers when Docker runs.
- `npm run build`
- `docker compose up --build` live checks for health, isolation, idempotency, Redis TTL, rate limit, suspension, worker.

## Review and verification

- Generated code was reviewed against the existing package structure and assessment constraints (no app-issued JWT, no fake analytics, no disabled tests).
- Automated verification: `.\mvnw.cmd -B clean verify` (H2 + mock Redis, RSA test JWTs — not live Keycloak).
- Live Docker/Keycloak/Redis/EXPLAIN/backup results are recorded only when those commands actually ran. If the engine was down, documents mark those items UNVERIFIED.

## Human decisions

- Keep membership RBAC in PostgreSQL; Keycloak only authenticates.
- Redis writes fail-closed (503); analytics cache fail-open.
- Swagger left public for local demo (documented in `SECURITY.md`).
- Compose local fallbacks `tenant`/`admin` remain for a one-command demo; `.env.example` uses placeholders.
- Tests stay on H2/mock Redis so `verify` does not require Docker.

AI was not used as a substitute for running Maven tests or for inventing Docker/EXPLAIN output.
