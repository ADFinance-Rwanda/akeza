# ADR 0006: Assessment RBAC mapping

## Status
Accepted

## Context
The assessment requires SUPER_ADMIN, ORG_ADMIN, PROJECT_MANAGER, and MEMBER. The running app used membership roles OWNER, ADMIN, MEMBER, VIEWER. VIEWER is not in the assessment. SUPER_ADMIN is platform-wide, not an organization membership.

## Decision
Membership roles (PostgreSQL `memberships.role`):

| Previous | New | Meaning |
| --- | --- | --- |
| OWNER | ORG_ADMIN | Organization management (members, delete org, projects, all tasks) |
| ADMIN | PROJECT_MANAGER | Project create/delete and all task writes in the org |
| MEMBER | MEMBER | Create tasks; update only tasks they created; cannot delete tasks |
| VIEWER | MEMBER | Assessment has no read-only role; existing viewers become members |

SUPER_ADMIN is `users.super_admin`, synced each request from the Keycloak realm role `SUPER_ADMIN`. It is not assignable via add-member. A super admin may act in any organization when they send `X-Organization-Id`; membership is not required.

ORG_ADMIN cannot be granted through add-member (same rule as former OWNER). PROJECT_MANAGER can be granted only by ORG_ADMIN or SUPER_ADMIN. Task deletion is ORG_ADMIN, PROJECT_MANAGER, or SUPER_ADMIN only — hiding the UI button is not the control.

## Alternatives
- Keep VIEWER: fails the four-role contract.
- Store SUPER_ADMIN as a membership: wrong scope (it is not tenant-bound).

## Consequences
Dave (former VIEWER) can write tasks after migration. Demo user `erin` holds realm role SUPER_ADMIN for live checks.
