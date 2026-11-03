# Spec Delta

## ADDED Requirements

### Requirement: Services validate JWT as resource servers

REST-facing services (`chats`, `messages`, `notifications`) SHALL validate incoming JWT access tokens using the JWKs endpoint published by the `users` service. Each service SHALL configure `spring-boot-starter-oauth2-resource-server` with the issuer URI of the `users` service. The `users` service SHALL validate its own tokens in its default security filter chain.

#### Scenario: Valid token accepted

- **WHEN** a request includes `Authorization: Bearer <jwt>` with a valid, unexpired token signed by the `users` service
- **THEN** the service SHALL accept the request and extract the user id from the `sub` claim.

#### Scenario: Missing or invalid token rejected

- **WHEN** a request to an authenticated endpoint lacks a Bearer token or includes an invalid/expired token
- **THEN** the service SHALL return `401 Unauthorized`.

#### Scenario: Insufficient role rejected

- **WHEN** a request includes a valid token but the user lacks the required role (e.g. `POST /users` without `ROLE_ADMIN`)
- **THEN** the service SHALL return `403 Forbidden`.