# ADR 0001: Keycloak is the identity provider

## Status
Accepted

## Context
The assessment requires OIDC/Keycloak rather than an application-issued JWT. A local login endpoint would compete with that requirement and create a second trust path.

## Decision
The API is a Spring Security OAuth2 resource server. Local `users` rows are provisioned from the Keycloak subject. Memberships remain in PostgreSQL because they are tenant-specific.

## Alternatives
- Application JWT login (`/api/auth/login`): rejected; it fails the Keycloak requirement.
- Keycloak roles as the only authorization: rejected; org membership is per-tenant data.

## Consequences
Demo users are created in the Keycloak realm import. The old `/api/auth/register` and `/api/auth/login` endpoints were removed. Browser login uses `task-web` with PKCE; the API never issues tokens.
