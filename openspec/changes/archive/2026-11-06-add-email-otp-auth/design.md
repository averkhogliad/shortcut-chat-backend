# Design

## Context

The `users` service owns `User { id, name, email }` via Spring Data JDBC + PostgreSQL + tx-outbox-starter. It publishes `UserCreated`, `UserUpdated`, `UserDeleted` to `UserLifecycle` and `SendEmail` to `MailerCommands` through the transactional outbox. The `mail-sender` service consumes `MailerCommands` and sends emails, storing delivery state in Redis.

No Spring Security is currently configured. Identity is a dev-only `X-User-Id` header. The project conventions prohibit JPA/Hibernate; Spring Data JDBC is the persistence layer. The reference implementation at `C:\Projects\courses\async-architecture-course\auth` uses Spring Authorization Server 1.1.1 with JPA — this design adapts it to Spring Data JDBC and the shortcut-chat outbox pattern.

## Goals / Non-Goals

**Goals:**

- Passwordless email OTP authentication with auto-registration.
- OIDC provider infrastructure (JWKs, discovery, userinfo, refresh tokens).
- Reuse existing mail-sender pipeline for OTP email delivery.
- No changes to Kafka contracts or downstream consumers.

**Non-Goals:**

- JWT enforcement in other services or gateway (out of scope — X-User-Id stays as fallback).
- Token introspection endpoint (JWT validation is local via JWKs).
- Email change feature (separate future change).
- Role-based access control beyond `POST /users` admin-only.
- JWK persistence (in-memory keypair regenerated on restart is acceptable).
- SSE authentication migration (token in query param is a future concern).

## Decisions

### 1. Spring Authorization Server with custom grant type

Use `spring-security-oauth2-authorization-server` (current stable version) with a custom `urn:shortcut:oauth:grant-type:email-otp` grant type.

**Why:** Spring Authorization Server provides JWKs, discovery, userinfo, refresh tokens, and standard error responses. The custom grant type is a well-defined extension point requiring 4 classes: `EmailOtpGrantAuthenticationToken`, `EmailOtpGrantAuthenticationConverter`, `EmailOtpGrantAuthenticationProvider`, and registration in `SecurityConfig`.

**Alternative:** Custom token endpoint with nimbus-jose-jwt. Rejected: would require implementing JWKs, discovery, userinfo, and refresh manually — more code, more bugs, less compliant.

### 2. OTP stored in Redis, not PostgreSQL

OTP codes are stored in Redis under `users:otp:{email}` with TTL 300s. The value is a JSON object `{ code, attempts }`. Attempt counter is decremented atomically via Redis operations.

**Why:** Natural TTL, atomic operations, ephemeral data does not belong in the persistent database. Redis is already in the infrastructure (mail-sender uses it). `users` service gains `spring-boot-starter-data-redis` dependency.

**Alternative:** PostgreSQL `otp_codes` table with scheduled cleanup. Rejected: adds cleanup complexity, no atomic attempt counter, mixes ephemeral data with persistent data.

### 3. User not created on OTP request, only on verification

`POST /auth/code` generates and stores the OTP but does NOT create a user. User creation happens in `EmailOtpGrantAuthenticationProvider` on successful code verification, inside a `@Transactional` method that also writes the `UserCreated` outbox record.

**Why:** Prevents ghost users from spam OTP requests. User creation + outbox write is atomic and only fires for verified emails.

**Alternative:** Create user on OTP request. Rejected: every spam request creates a user row and fires `UserCreated`, polluting projections.

### 4. Auto-registration derives name from email

`name = email.substringBefore("@")`. The user can edit `name` later via `PUT /users/{id}`. `email` is immutable.

**Why:** No name is available at auto-registration time. Deriving from email is the least-surprising default. Email immutability ensures the auth identity (email) cannot be decoupled from the profile.

### 5. JWK persisted to file

RSA keypair is stored in a JSON file on disk (path configurable via `spring.security.oauth2.authorizationserver.jwk.file`). On startup, the service loads the key from the file. If the file does not exist, a new RSA keypair is generated and written. Tokens survive restarts and deploys.

**Why:** Persistent keys are required for production-grade auth. In-memory keys invalidate all tokens on every restart, which is unacceptable for any real deployment. File-based storage is the simplest persistent option — no database schema changes, no external secret manager.

**Alternative:** In-memory keypair. Rejected: tokens invalidated on restart. PostgreSQL table. Rejected: adds schema and migration for a single-row table; file is simpler.

### 6. SecurityConfig: two filter chains

- Chain 1 (order 1): Auth server endpoints (`/oauth2/**`, `/.well-known/**`, `/userinfo`) — OIDC configuration, no form login.
- Chain 2 (order 2): All other endpoints — `permitAll` for now (JWT enforcement is out of scope). `POST /users` requires `ROLE_ADMIN` once JWT is present; until then, it remains open but documented as admin-only in the spec.

**Why:** The auth server needs its own security chain for OAuth2 endpoints. The rest of the service keeps current behavior (no auth) until the gateway/services enforce JWT.

### 7. OTP email via existing SendEmail outbox event

`POST /auth/code` emits a `SendEmail` outbox event (type `SendOtpCode`) with body `EmailMessage { to=email, subject="Your code", body=code }`. The `mail-sender` consumer handles it the same way as welcome emails — no changes to mail-sender.

**Why:** Reuses the existing outbox → Kafka → mail-sender pipeline. No new topics, no new consumers.

**Alternative:** New `SendOtpCode` event type with separate topic. Rejected: unnecessary — `SendEmail` payload is generic enough. The outbox route maps `SendOtpCode` type to `MailerCommands` topic with event name `SendEmail`, same as `SendCreatedUserNotification`.

### 8. Rate limiting via Redis TTL

Rate limit is enforced by checking for a rate-limit key `users:otp:ratelimit:{email}` in Redis before generating a code. If the key exists (TTL not expired), return 429. On success, set the key with TTL 60s.

**Why:** Simple, uses the same Redis instance, no additional dependencies.

## Risks / Trade-offs

- [Email delivery dependency] → If mail-sender is down, no one can log in. Mitigation: outbox retries delivery. Accepted for now.
- [JWK file security] → Key file on disk must be protected (file permissions, not committed to repo). Mitigation: path is configurable via properties/env, file is in `.gitignore`, documented in deployment guide.
- [No token introspection] → Services must fetch JWKs and validate locally. Mitigation: Spring Security OAuth2 Resource Server handles this automatically when configured.
- [Rate limit in Redis] → Redis unavailability blocks OTP requests. Mitigation: Redis is already infrastructure for mail-sender. Accepted.
- [POST /users admin-only not enforced yet] → Spec requires admin role but enforcement depends on JWT in requests. Until gateway sends JWT, endpoint stays open. Documented as a known gap.

## Open Questions

(none)