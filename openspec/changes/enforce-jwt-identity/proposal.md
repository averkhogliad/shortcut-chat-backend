# Proposal

## Why

The `add-email-otp-auth` change introduces a JWT OIDC provider in the `users` service, but token enforcement is out of its scope. Services still use the dev-only `X-User-Id` header and `userId` query param for identity. Without JWT enforcement, any client can impersonate any user by sending an arbitrary UUID header. This change replaces the dev mechanism with Bearer token validation across all REST-facing services.

## What Changes

- Add `spring-boot-starter-oauth2-resource-server` to `chats`, `messages`, and `notifications`.
- `chats` and `messages` (Spring MVC): configure JWT resource server, validate tokens via JWKs from `users` service, extract `user.id` from JWT `sub` claim.
- `notifications` (WebFlux): configure reactive JWT resource server, validate via JWKs, extract `user.id` from `sub`.
- **BREAKING**: `GET /chats` — replace `X-User-Id` header with `Authorization: Bearer <jwt>`; user.id extracted from `sub` claim.
- **BREAKING**: `POST /messages` — replace `X-User-Id` header with `Authorization: Bearer <jwt>`; author extracted from `sub` claim.
- **BREAKING**: `GET /subscribe?userId=...` — replace `userId` query param with `token` query param (`?token=<jwt>`); user.id extracted from `sub` claim. SSE EventSource API does not support custom headers, so token is passed as query parameter.
- `users` service: SecurityConfig chain 2 enforces authentication on all `/users/**` endpoints; `POST /users` and `DELETE /users/{id}` require `ROLE_ADMIN` from JWT claims.
- `mail-sender`: not affected (Kafka consumer, no REST, no user identity).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/auth`: add resource server requirements — services validate JWT via JWKs, extract user.id from `sub`.
- `system/rest-api`: replace `X-User-Id` header with `Authorization: Bearer`; replace `userId` SSE query param with `token`.
- `system/architecture`: services MAY depend on `spring-boot-starter-oauth2-resource-server`; all REST-facing services validate JWT.

## Impact

- **Code**: `chats`, `messages` — new `SecurityConfig`, controller changes (remove `@RequestHeader("X-User-Id")`, add `Authentication` parameter). `notifications` — new reactive `SecurityWebFilterChain`, `NotificationSubscriptionHandler` changes (remove `userId`, add `token` validation). `users` — SecurityConfig chain 2 updated.
- **POM**: `chats`, `messages`, `notifications` gain `spring-boot-starter-oauth2-resource-server`.
- **REST**: Breaking changes to `GET /chats`, `POST /messages`, `GET /subscribe`.
- **Kafka**: no changes.
- **Tests**: existing endpoint tests need auth setup (mock JWT or test tokens). New tests for 401 (no token), 403 (insufficient role), 200 (valid token).
- **Dependencies**: `enforce-jwt-identity` depends on `add-email-otp-auth` (JWKs endpoint must exist).