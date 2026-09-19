# ADR 0002: Tenant context via membership + header

## Status
Accepted

## Context
Users can belong to multiple organizations. JWT does not carry a single org id.

## Decision
Require `X-Organization-Id` on tenant-scoped routes and verify membership server-side. All project/task queries include `organization_id`. Cross-tenant ids return 403 (not a member) or 404 (resource filtered by org).

## Alternatives
- One organization per JWT / separate Keycloak realm per tenant: too heavy for this assessment.
- Trust a tenant id from the client without membership check: insecure.

## Consequences
Clients must send the header. A missing/invalid header is 403, not a chance to scan other tenants.
