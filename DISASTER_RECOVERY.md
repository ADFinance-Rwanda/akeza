# Disaster Recovery

This document describes **what this Compose stack actually does**, not hosted-cloud RTO/RPO numbers. There is no replicated Postgres, no managed Redis failover, and no off-site backup unless you copy `backups/` yourself.

## Actual guarantees

- PostgreSQL data lives in the `postgres_data` volume until `docker compose down -v`.
- Redis AOF lives in `redis_data`. Cache and rate-limit keys can be rebuilt or will expire. Redis is **not published on the host**; app and worker reach it on the Compose network. Inspect with `docker compose exec redis redis-cli`.
- Keycloak data lives in `keycloak_data`. `--import-realm` loads `keycloak/realm-taskmgr.json` when the realm is absent. Recreating only the Keycloak volume re-imports demo users; `UserSyncService` remaps `keycloak_sub` by email so Postgres memberships survive.
- Flyway owns the schema (`ddl-auto=none`). Restoring a dump restores both schema and data.
- The worker queue is the `jobs` table in Postgres, so it is included in `pg_dump`.

## Assumptions

- You are running the repo's `docker-compose.yml` on a single machine.
- Compose default credentials (`tenant` / `admin` unless overridden in `.env`) are local demo only.
- `docker compose down -v` is destructive.

## PostgreSQL failure

### Restore process

1. Keep or copy a dump from `scripts/backup.ps1` or `scripts/backup.sh` (creates `backups/tenant_management-*.sql`).
2. If the volume is corrupt: `docker compose down -v` then `docker compose up -d` to recreate empty Postgres, **or** restore into the existing `db` service.
3. Restore:

```powershell
.\scripts\restore.ps1 -BackupFile .\backups\tenant_management-YYYYMMDD-HHMMSS.sql
```

```bash
./scripts/restore.sh backups/tenant_management-YYYYMMDD-HHMMSS.sql
```

The script stops `app` and `worker`, drops/creates `tenant_management`, restores the SQL, and starts `app`/`worker` again.

Manual equivalent:

```powershell
docker compose exec -T db pg_dump -U tenant -d tenant_management --no-owner --no-acl --clean --if-exists > backups\manual.sql
docker compose stop app worker
docker compose exec -T db psql -U tenant -d postgres -c "DROP DATABASE IF EXISTS tenant_management;"
docker compose exec -T db psql -U tenant -d postgres -c "CREATE DATABASE tenant_management;"
Get-Content backups\manual.sql | docker compose exec -T db psql -U tenant -d tenant_management
docker compose start app worker
```

### Verification

- `docker compose ps` — `db` healthy, `app` healthy.
- `GET http://localhost:8080/ready`
- Confirm organizations, memberships, projects, tasks, audit_logs, and jobs exist (`DATABASE.md`).

### Application recovery

The API does not store tenant data locally. After Postgres is restored and ready, restart `app`/`worker` if they are crash-looping. Flyway will not re-apply old versions if `flyway_schema_history` is in the dump.

## Redis failure

### Cache loss behavior

Analytics cache keys are `analytics::org:{organizationId}`. The API writes them with Redis `SET` + TTL (`app.analytics-cache-ttl-seconds`, default 60). If Redis is empty or restarted, the next dashboard call computes from Postgres and refills the cache (fail-open). After a successful dashboard GET, `TTL` on the key must be > 0.

### Rebuilding cache

No manual rebuild is required. Hitting `GET /api/analytics/dashboard` with a valid tenant header rebuilds that org's entry.

### Rate-limit state

Write counters are `ratelimit:{orgId}:{userId}:{METHOD}:/tasks`. Loss of Redis **clears** the current window (clients can burst again). If Redis is **down** while the API is up (measured 2026-09-19 after `docker compose build --no-cache`):

- `GET /health` → 200 in ~150ms (process liveness).
- `GET /ready` → 503 `{"status":"DOWN"}` in ~2.5s (3s abort around the readiness group).
- `GET /api/tasks` → 200 in ~300ms.
- `GET /api/analytics/dashboard` → 200 in ~4.7s (cache miss/fail-open to Postgres after the 2s Redis timeout).
- Task POST/PUT → 503 `Rate limiting is unavailable. Try again later.` in ~2.2–2.5s. Idempotency is PostgreSQL-backed and is not skipped.
- After `docker compose start redis`, `GET /ready` returned 200 in ~400ms.

## Worker failure

- Compose `restart: unless-stopped` restarts the `worker` container.
- Pending jobs remain `PENDING` in Postgres until claimed.
- A job left in `PROCESSING` after a crash is reclaimed to `PENDING` after 2 minutes (`JobProcessor` stale reclaim). Until then it will not be retried.
- Failed attempts increment `attempts` and reschedule `available_at`. After `max_attempts` the row is `FAILED`.
- Unsupported job types are marked `FAILED` immediately (not `DONE`) with `last_error` preserved.
- `JobRetentionJob` deletes `DONE` jobs older than 24h and `FAILED` jobs older than 168h (`JOB_DONE_RETENTION_HOURS` / `JOB_FAILED_RETENTION_HOURS`). Active `PENDING`/`PROCESSING` rows are never deleted.

## Backend failure

- Restart: `docker compose restart app` or `restart: unless-stopped`.
- Readiness: `GET /ready` (process + Postgres + Redis, 3s abort). Liveness: `GET /health` (process only).
- Data persistence is Postgres, not the API container.

## Keycloak failure

- Unauthenticated browsers cannot log in. Existing access tokens remain valid until expiry if the API can still fetch JWKS; if Keycloak is down, JWKS refresh can fail and new/rotated keys will not load.
- Recovery: `docker compose restart keycloak`. Realm import applies when the realm is absent in the `keycloak_data` volume. Restarting the container with that volume keeps runtime changes.
- Recreating **only the Keycloak volume** (`docker compose down` then removing `keycloak_data`) re-imports demo users from `keycloak/realm-taskmgr.json` and issues new `sub` values. `UserSyncService` rebinds an existing `users` row by email and updates `keycloak_sub` so memberships survive.

## Recommended operational procedures

- Run `.\scripts\backup.ps1` before demos and after seed data.
- Copy `backups/` off the machine if the assessment environment can be wiped.
- Do not use `docker compose down -v` unless you intend to destroy Postgres and Redis volumes.
