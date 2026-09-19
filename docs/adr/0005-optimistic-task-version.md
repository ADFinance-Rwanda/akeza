# ADR 0005: Optimistic concurrency on tasks

## Status
Accepted

## Context
Two users can edit the same task. Last-write-wins would silently drop an update, which fails the assessment's conflict requirement.

## Decision
Persist `tasks.version` and map it with JPA `@Version`. Updates must send the version from the last GET/create. If it does not match, the API returns HTTP 409. Hibernate also rejects concurrent flushes of the same version.

## Alternatives
- Pessimistic row locks: worse UX for a task UI.
- Frontend-only version check: bypassable.

## Consequences
Clients must reload after 409. The React UI surfaces that conflict.
