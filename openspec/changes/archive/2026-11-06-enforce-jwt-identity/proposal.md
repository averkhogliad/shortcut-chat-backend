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
- `users` service: SecurityConfig chain 2 enforces authentication on all `/users/**` endpoints. Self-service operations use the new `/users/me` namespace (`GET`, `PUT`, `DELETE`), with identity from `sub`. Management endpoints keep their current paths under an interim policy.
- `users` OTP endpoints (`POST /auth/code`, `/oauth2/**`, `/.well-known/**`) stay accessible without a token.
- `mail-sender`: not affected (Kafka consumer, no REST, no user identity).

> **Scope amendment — roles deferred.** The role model, a `roles` claim, `hasRole` policies, and an admin panel are out of scope and deferred to a separate change. The interim management policy is: `GET /users/{id}`, `GET /users/by-email`, `POST /users` — authenticated; `PUT /users/{id}`, `DELETE /users/{id}` — self only. The main-spec scenario "POST /users requires admin role" cannot be implemented in this cycle (no roles claim; current code applies `permitAll`) and is carried in the delta as deferred.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/auth`: add resource server requirements — services validate JWT via JWKs, extract user.id from `sub`.
- `system/rest-api`: replace `X-User-Id` header with `Authorization: Bearer`; replace `userId` SSE query param with `token`; add `/users/me` self-service endpoints and the interim management policy (deferred until roles).
- `system/architecture`: services MAY depend on `spring-boot-starter-oauth2-resource-server`; all REST-facing services validate JWT; `common` provides the shared identity extraction helper.

## Impact

- **Code**: `chats`, `messages` — new `SecurityConfig`, controller changes (remove `@RequestHeader("X-User-Id")`, add identity from the JWT `sub`). `notifications` — new reactive `SecurityWebFilterChain`, `NotificationSubscriptionHandler` changes (remove `userId`, add `token` validation). `users` — SecurityConfig chain 2 updated, `/users/me` self-service endpoints added.
- **POM**: `chats`, `messages`, `notifications` gain `spring-boot-starter-oauth2-resource-server`.
- **REST**: Breaking changes to `GET /chats`, `POST /messages`, `GET /subscribe`; new `/users/me` endpoints; management endpoints keep their paths.
- **Kafka**: no changes.
- **Tests**: existing endpoint tests need auth setup (mock JWT or test tokens). New tests for 401 (no token), 403 (forbidden self-access on another user), 200 (valid token). Admin-role tests are deferred until roles.
- **Dependencies**: `enforce-jwt-identity` depends on `add-email-otp-auth` (JWKs endpoint must exist).