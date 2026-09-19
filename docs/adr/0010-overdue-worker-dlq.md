# ADR 0010: Overdue scan worker and job dead-letter state

## Status
Accepted

## Context
`INVALIDATE_ANALYTICS` duplicated the synchronous Redis cache evict. The worker still needs a job that does real work, plus inspectable terminal failure. Removing Tomcat from the worker would break the image `HEALTHCHECK` that curls `/ready`.

## Decision
Keep cache eviction synchronous (Phase 1). Add `SCAN_OVERDUE`: the worker enqueues at most one pending/processing scan, then marks due incomplete tasks with `overdue_notified_at` and writes `TASK_OVERDUE` audit rows (idempotent). Jobs that exhaust `max_attempts` stay `FAILED` with `dead_lettered_at` set; ORG_ADMIN can list them at `GET /api/organizations/{id}/jobs/dead-letters`. The worker JVM still serves Actuator for health checks and does not publish a host port.

## Alternatives
- `spring.main.web-application-type=none`: cleaner process, broken image healthcheck.
- Separate `job_dead_letters` table: extra schema for the same rows.

## Consequences
Retry, stale reclaim, and FAILED-as-DLQ remain in PostgreSQL. Duplicate overdue audits are prevented by `overdue_notified_at`.
