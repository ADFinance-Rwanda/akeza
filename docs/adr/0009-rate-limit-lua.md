# ADR 0009: Atomic Redis rate-limit INCR+EXPIRE

## Status
Accepted

## Context
Rate limits used `INCR` then a separate `EXPIRE`. A crash between those calls could leave a key with TTL -1. Fail-closed HTTP 503 on Redis errors is required for writes.

## Decision
Apply a Lua script that `INCR`s, then `EXPIRE`s if `TTL < 0`, and returns `{count, ttl}` in one Redis round trip. Fail-closed (503) is unchanged: writes do not proceed without Redis, so PostgreSQL idempotency is never skipped. Lettuce `commandTimeout` is 2s so a dead Redis does not hang the request for a minute. Analytics cache remains fail-open. `/ready` fails in at most 3s.

## Alternatives
- `MULTI/EXEC`: extra round trips and still racy with concurrent clients without WATCH.
- Fail-open on Redis down: would skip the write limiter.

## Consequences
New keys always receive an expiration. Tests cover Lua TTL via embedded Redis and Testcontainers when Docker is available.
