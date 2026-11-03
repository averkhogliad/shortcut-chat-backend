# Proposal

## Why

The backend has no authentication. Identity is a dev-only `X-User-Id` header. The `users` service owns `User { id, name, email }` but has no credentials, no token issuance, and no login flow. Adding passwordless email OTP authentication eliminates passwords entirely, reuses the existing mail-sender pipeline for code delivery, and auto-registers users on first login — removing signup friction.

## What Changes

- Merge auth provider role into the `users` service (no new microservice).
- Add Spring Security OAuth2 Authorization Server to `users`: JWKs, OIDC discovery, userinfo, refresh tokens.
- Add custom grant type `urn:shortcut:oauth:grant-type:email-otp` for passwordless authentication via email OTP.
- Add `POST /auth/code { email }` endpoint: generate 6-digit OTP, store in Redis (TTL 5 min, max 3 attempts), emit `SendEmail` via outbox. Always returns 202 (no user existence leak).
- Add `POST /oauth2/token` with email-otp grant: verify code, find-or-create user by email, issue JWT.
- Auto-registration on successful OTP verification: if user does not exist, create `User { id=UUID, name=email.substringBefore("@"), email }`, publish `UserCreated` via outbox.
- Add Redis dependency to `users` for OTP storage (keys `users:otp:{email}`, TTL 5 min, atomic attempt counter).
- Rate limit: 1 OTP request per email per 60 seconds.
- **BREAKING**: `PUT /users/{id}` — `email` becomes immutable. If request email differs from existing, return 400. `name` remains editable.
- `POST /users` becomes admin-only (not public registration). Registration happens through the OTP flow.

## Capabilities

### New Capabilities

- `system/auth`: Email OTP authentication, OIDC provider (token issuance, JWKs, discovery, userinfo), auto-registration, OTP lifecycle (TTL, attempts, rate limiting).

### Modified Capabilities

- `system/rest-api`: New endpoint `POST /auth/code`; `PUT /users/{id}` email immutability; `POST /users` becomes admin-only.
- `system/architecture`: `users` service role expands to auth provider; `users` gains Redis as ephemeral store for OTP.
- `system/runtime-guarantees`: OTP storage and TTL; auto-registration transaction boundary; rate limiting.

## Impact

- **Code**: `users` module — new SecurityConfig, AuthServerConfig, custom grant type (4 classes), OTP service, Redis config, UserDetailsService. POM gains `spring-security-oauth2-authorization-server`, `spring-boot-starter-security`, `spring-boot-starter-data-redis`.
- **DB**: `users` DB — no schema changes to `users` table (no password column needed). OTP stored in Redis, not PostgreSQL.
- **REST**: New `POST /auth/code` endpoint. OIDC endpoints auto-configured by Spring Authorization Server (`/oauth2/token`, `/oauth2/authorize`, `/oauth2/jwks`, `/.well-known/openid-configuration`, `/userinfo`).
- **Kafka**: No changes — reuses existing `MailerCommands` / `SendEmail` for OTP email delivery. `UserCreated` event unchanged (fired on auto-registration, same payload).
- **Dependencies**: New compile deps in `users` POM. Redis already in docker-compose.
- **Consumers**: `chats` and `messages` unaffected — `UserCreated` payload and `UserLifecycle` topic unchanged.
- **Tests**: New tests for OTP flow, token issuance, auto-registration, email immutability, rate limiting.