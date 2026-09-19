# ADR 0008: PostgreSQL idempotency with wait-and-replay

## Status
Accepted

## Context
Task create is idempotent on `(organization_id, user_id, key_value)`. Sequential replay worked. Concurrent duplicates could return `409 in progress`, and a crash after reserve could leave `response_status = 0` forever. Completed keys had no retention.

## Decision
Keep PostgreSQL as the source of truth (not Redis). Reserve in a `REQUIRES_NEW` transaction. Concurrent losers poll until the winner completes, then replay the stored body. In-progress rows older than `app.idempotency.stale-seconds` (default 30) are deleted and the next caller may reserve. Failed business operations `abandon` (delete) the row so the same key can retry. Completed rows get `completed_at` and are purged after `app.idempotency.retention-hours` (default 24).

## Alternatives
- Redis-only keys: faster TTL, but a Redis wipe would allow duplicate creates.
- Immediate 409 on in-progress: simpler, but does not meet “exactly one task” plus safe replay under concurrency.

## Consequences
Duplicate POSTs with the same key create one task. Clients may wait up to `wait-ms * wait-attempts`. Cleanup is scheduled on the API process.
