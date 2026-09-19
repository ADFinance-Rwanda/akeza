# ADR 0003: Postgres jobs table as the worker queue

## Status
Accepted

## Context
The assessment needs an asynchronous worker with retry, not a synchronous fake. Redis is already used for cache and rate limits; using it as the only queue would lose jobs if Redis is wiped.

## Decision
Persist jobs in PostgreSQL. The `worker` Spring profile polls due `PENDING` rows. `JobProcessor` claims a row with `UPDATE ... SET status = PROCESSING WHERE status = PENDING` so two workers cannot process the same job. Failures go back to `PENDING` with backoff, then `FAILED` after `max_attempts`.

## Alternatives
- Redis lists/streams: faster, but jobs would disappear on Redis volume loss.
- In-process `@Async` only: no retry after process restart.

## Consequences
Jobs survive app restarts. `SCAN_OVERDUE` writes `TASK_OVERDUE` once per task (`overdue_notified_at`). Terminal failures are `FAILED` + `dead_lettered_at`. Compose runs a second JVM with `SPRING_PROFILES_ACTIVE=prod,worker` and the same image healthcheck (Actuator). See ADR 0010.
