# ADR 0007: Project status

## Status
Accepted

## Context
The assessment requires `projects.status`. Organization already uses a small string enum (`ACTIVE` / `SUSPENDED`).

## Decision
`ProjectStatus`: `ACTIVE` (default) or `ARCHIVED`.

Archived projects remain visible and tenant-scoped. Tasks can still be listed; write APIs still require the same RBAC. No extra workflow states (no IN_PROGRESS on projects).

## Alternatives
- Copy organization SUSPENDED: confusing for a project.
- Free-text status: weaker integrity.

## Consequences
Existing rows migrate to ACTIVE. Clients may set status on create or PUT `/api/projects/{id}`.
