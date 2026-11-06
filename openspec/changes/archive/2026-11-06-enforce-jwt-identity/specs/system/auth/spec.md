# Spec Delta

## ADDED Requirements

### Requirement: Services validate JWT as resource servers

REST-facing services (`chats`, `messages`, `notifications`) SHALL validate JWT access tokens using the JWKs published by `users`. Each service SHALL configure `spring-boot-starter-oauth2-resource-server` with the `users` issuer URI. The `users` service SHALL validate its own tokens in its default security filter chain and SHALL extract the caller id from `sub`. Role-based checks SHALL be deferred: this cycle SHALL NOT require a `roles` claim.

#### Scenario: Valid token accepted

- **WHEN** a request includes `Authorization: Bearer <jwt>` with a valid, unexpired token signed by the `users` service
- **THEN** the service SHALL accept the request and extract the user id from the `sub` claim.

#### Scenario: Missing or invalid token rejected

- **WHEN** a request to an authenticated endpoint lacks a Bearer token or includes an invalid/expired token
- **THEN** the service SHALL return `401 Unauthorized`.

#### Scenario: Insufficient role rejected

This scenario is **deferred until roles**. Access tokens issued in this cycle carry no `roles` claim, and the current `users` API chain applies `permitAll`. This change replaces `permitAll` with `authenticated()` only.

- **WHEN** a request includes a valid token without a `roles` claim and targets an endpoint whose admin-only policy is deferred
- **THEN** the service SHALL accept the authenticated request and SHALL NOT require a role in this cycle
- **AND** the `403 Forbidden` admin-only behaviour SHALL be deferred until a separate roles change.

### Requirement: Service-to-service authentication

The `users` service SHALL issue access tokens to registered service clients through the OAuth2 `client_credentials` grant. The calling service SHALL obtain such a token and send it as `Authorization: Bearer <jwt>`. The receiving service SHALL validate a service token as any other JWT issued by `users`; roles, scopes, and an end-user identity SHALL NOT be required. In this cycle, `notifications` SHALL authenticate to `chats` this way.

#### Scenario: Service token accepted by chats

- **WHEN** `notifications` calls `GET /members` on `chats` with a service token obtained through the `client_credentials` grant
- **THEN** `chats` SHALL validate it as any other valid JWT issued by `users`
- **AND** the response status SHALL be `200`.

#### Scenario: Service call without token rejected

- **WHEN** `GET /members` on `chats` is called without a Bearer token
- **THEN** the response status SHALL be `401`.
