# Tasks

## 1. POM and dependencies

- [ ] 1.1 Add `spring-security-oauth2-authorization-server`, `spring-boot-starter-security`, `spring-boot-starter-data-redis` to `users/pom.xml`. Verify: `.\mvnw.cmd -pl users -am compile` succeeds.
- [ ] 1.2 Add Redis connection properties to `users/src/main/resources/application.yaml` (`spring.data.redis.host`, `spring.data.redis.port`). Verify: application context starts with Redis connection (Testcontainers or local Redis).

## 2. Security and auth server configuration

- [ ] 2.1 Create `SecurityConfig` in `users/config`: filter chain 1 (auth server, OIDC enabled, no form login), filter chain 2 (default, `permitAll` for now, `POST /users` requires `ROLE_ADMIN`). Verify: context starts, auth server endpoints respond.
- [ ] 2.2 Create `AuthServerConfig` with `AuthorizationServerSettings` (issuer from properties). Create `AuthServerProperties` binding `spring.security.oauth2.authorizationserver`. Verify: `GET /.well-known/openid-configuration` returns discovery document.
- [ ] 2.3 Create `JwkSource` bean loading RSA keypair from file (path from `AuthServerProperties.jwk.file`). On startup: if file exists, load keypair; if not, generate new RSA keypair and write to file. Create `JwtDecoder` bean. Create `jwk.kt` utility (generateRsa, saveToFile, loadFromFile). Verify: first startup creates file, second startup loads same key, `GET /oauth2/jwks` returns consistent key across restarts.
- [ ] 2.4 Configure OAuth2 registered client (client-id, client-secret, grant types: `authorization_code`, `refresh_token`, `urn:shortcut:oauth:grant-type:email-otp`, scopes: `openid`, `profile`). Verify: discovery document lists all grant types.
- [ ] 2.5 Create `OAuth2TokenCustomizer` adding `email` and `name` claims to ID tokens. Verify: issued ID token contains `email` and `name`.

## 3. OTP service and Redis storage

- [ ] 3.1 Create `OtpService` in `users/core/service`: `requestCode(email)` — check rate limit key `users:otp:ratelimit:{email}` (return false if exists), generate 6-digit code, store `{ code, attempts:3 }` in `users:otp:{email}` with TTL 300s, set rate limit key TTL 60s, return code. `verifyCode(email, code)` — load from Redis, compare, decrement attempts on mismatch, delete on success or exhaustion, return result enum. Verify: unit test with mock `RedisTemplate` — generate, verify success, verify wrong code, verify exhausted, verify expired.
- [ ] 3.2 Create `RedisConfig` in `users/config` with `RedisTemplate<String, String>` bean. Verify: context starts, Redis operations work.

## 4. OTP request endpoint

- [ ] 4.1 Create `AuthController` in `users/api/controller`: `POST /auth/code` accepting `{ email }`, calls `OtpService.requestCode`, emits `SendEmail` outbox event (type `SendOtpCode`, body `EmailMessage { to=email, subject, body=code }`). Returns `202` always on success, `429` if rate limited. Verify: unit test with mock `OtpService` and `OutboxEmitter` — success returns 202, rate-limited returns 429, outbox emit captured with correct payload.
- [ ] 4.2 Add outbox route for `SendOtpCode` → `MailerCommands` topic, event name `SendEmail`, in `users/application.yaml` `tx-outbox.routes`. Verify: application starts without route validation error.

## 5. Custom email-otp grant type

- [ ] 5.1 Create `EmailOtpGrantAuthenticationToken` (holds `email`, `code`, authenticated flag). Verify: compiles.
- [ ] 5.2 Create `EmailOtpGrantAuthenticationConverter` implementing `AuthenticationConverter` — extract `grant_type`, `email`, `code` from token request parameters, return `EmailOtpGrantAuthenticationToken` unauthenticated. Verify: unit test — valid params produce token, wrong grant type returns null.
- [ ] 5.3 Create `EmailOtpGrantAuthenticationProvider` implementing `AuthenticationProvider` — call `OtpService.verifyCode`, on success find user by email via `UserRepository`, if not found create user (`name=email.substringBefore("@"), email`) and emit `UserCreated` outbox in `@Transactional`, build `OAuth2AccessTokenAuthentication` with JWT. Verify: unit test with mocks — success issues token, wrong code fails, auto-registration creates user and emits outbox.
- [ ] 5.4 Register the converter and provider in `SecurityConfig` auth server chain. Verify: `POST /oauth2/token` with email-otp grant returns access_token, id_token, refresh_token.

## 6. Email immutability

- [ ] 6.1 In `UserService.update`, reject email change: if `user.email != existing.email`, return `EntityResult.Invalid("Email is immutable")`. Verify: unit test — same email updates name, different email returns Invalid.
- [ ] 6.2 In `UserController.update`, the existing behavior already unwraps `EntityResult`. Verify: integration test — `PUT /users/{id}` with different email returns 400 ProblemDetail.

## 7. Integration tests

- [ ] 7.1 Integration test: `POST /auth/code` → `POST /oauth2/token` with correct code → receive JWT → validate `sub`, `email`, `name` claims. Verify: test green (requires Testcontainers Postgres + Redis + Kafka).
- [ ] 7.2 Integration test: auto-registration — request code for unknown email, verify with code, assert user created in DB with `name=part-before-@`, `UserCreated` event in `UserLifecycle` topic. Verify: test green.
- [ ] 7.3 Integration test: wrong code 3 times → code invalidated → 4th attempt fails. Verify: test green.
- [ ] 7.4 Integration test: rate limit — two `POST /auth/code` within 60s → second returns 429. Verify: test green.
- [ ] 7.5 Integration test: `PUT /users/{id}` with different email → 400. Verify: test green.

## 8. Specs and verification

- [ ] 8.1 Verify `openspec validate --changes add-email-otp-auth --strict` passes.
- [ ] 8.2 Sync deltas to `openspec/specs/system/auth/spec.md` (new), `openspec/specs/system/rest-api/spec.md`, `openspec/specs/system/architecture/spec.md`, `openspec/specs/system/runtime-guarantees/spec.md`. Verify `openspec validate --specs` passes.
- [ ] 8.3 Run `.\mvnw.cmd -pl users -am test`. Verify: build and tests green (subject to Docker/Testcontainers availability).

## Workflow follow-up

- Archive the change after spec sync and verification.
- Verify the archived result.