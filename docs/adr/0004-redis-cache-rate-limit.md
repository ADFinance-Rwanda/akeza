# ADR 0004: Redis for analytics cache and write rate limits

## Status
Accepted

## Context
Dashboard analytics are read often and must stay tenant-isolated. Task POST/PUT/DELETE need a shared rate limiter so two API instances cannot each allow a full burst.

## Decision
Use Redis:

- Cache key `analytics::org:{organizationId}` with `SET` + TTL (fail-open).
- Rate-limit key `ratelimit:{orgId}:{userId}:{METHOD}:/tasks` via Lua `INCR`+`EXPIRE` (fail-closed 503).

See ADR 0009 for the Lua script.

## Alternatives
- Caffeine/in-memory cache or rate limits: incorrect with multiple app/worker containers.
- Rate-limit in the frontend: not a security boundary.

## Consequences
Compose includes Redis with AOF on the internal network (not published on the host). H2 tests mock Redis; embedded Redis and Testcontainers cover TTL and Lua when those environments run.
