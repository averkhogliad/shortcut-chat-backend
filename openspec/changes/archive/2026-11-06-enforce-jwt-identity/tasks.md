# Tasks

## 1. `users` service — endpoint security enforcement

- [ ] 1.1 Update `SecurityConfig` chain 2 in `users`: `/users/**` requires `authenticated()`; `POST /auth/code` and the authorization-server paths stay `permitAll`; `PUT /users/{id}` and `DELETE /users/{id}` require self (`#id == sub` via SpEL). **Roles deferred:** no `roles` claim and no `hasRole` in this cycle; ADMIN-only `POST /users` and ADMIN-or-self `PUT`/`DELETE` are deferred. Verify: valid token can GET/PUT/DELETE self; another id returns 403; no token returns 401.
- [ ] 1.2 Add the `/users/me` self-service endpoints (`GET`, `PUT`, `DELETE`) with identity from the JWT `sub` claim, including `@AuthenticationPrincipal`/shared identity extraction where the self-check needs it. Verify: user can read, update, and delete own profile; changing email returns 400; no token returns 401.

## 2. `chats` service — JWT resource server

- [ ] 2.1 Add `spring-boot-starter-oauth2-resource-server` to `chats/pom.xml`. Add `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `chats/application.yaml`. Verify: `.\mvnw.cmd -pl chats -am compile` succeeds.
- [ ] 2.2 Create `SecurityConfig` in `chats/config`: `SecurityFilterChain` with `oauth2ResourceServer().jwt()`, all `/chats/**` and `/members/**` endpoints require `authenticated()`, CSRF disabled (API). Verify: context starts, unauthenticated request returns 401.
- [ ] 2.3 Update `ChatController.list` (both overloads): replace `@RequestHeader("X-User-Id") userId: UUID` with `@AuthenticationPrincipal jwt: Jwt`, extract `userId = UUID.fromString(jwt.subject)`. Verify: integration test — valid JWT returns user's chats; no JWT returns 401.
- [ ] 2.4 Update `ChatController.create` and `ChatService.create`: extract user id from JWT `sub` claim and set as chat owner (`ChatEntity.owner = AggregateReference.to(user.id)`). Remove `owner = null // TODO` in `ChatConverter.toListItem` and `toDetails` — populate owner from entity. Verify: integration test — `POST /chats` with valid JWT creates chat with owner from `sub`; `GET /chats/{id}` returns owner; `GET /chats` list item includes owner.

## 3. `messages` service — JWT resource server

- [ ] 3.1 Add `spring-boot-starter-oauth2-resource-server` to `messages/pom.xml`. Add `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `messages/application.yaml`. Verify: `.\mvnw.cmd -pl messages -am compile` succeeds.
- [ ] 3.2 Create `SecurityConfig` in `messages/config`: `SecurityFilterChain` with `oauth2ResourceServer().jwt()`, all `/messages/**` endpoints require `authenticated()`, CSRF disabled. Verify: context starts, unauthenticated request returns 401.
- [ ] 3.3 Update `MessageController.create`: replace `@RequestHeader("X-User-Id") userId: UUID` with `@AuthenticationPrincipal jwt: Jwt`, extract `userId = UUID.fromString(jwt.subject)`, pass as `UserRef(userId)`. Verify: integration test — valid JWT creates message with author from `sub`; no JWT returns 401.

## 4. `notifications` service — reactive JWT resource server

- [ ] 4.1 Add `spring-boot-starter-oauth2-resource-server` to `notifications/pom.xml`. Add `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `notifications/application.yaml`. Verify: `.\mvnw.cmd -pl notifications -am compile` succeeds.
- [ ] 4.2 Create `SecurityConfig` in `notifications/config`: reactive `SecurityWebFilterChain` with JWT resource server. Create a custom `ServerAuthenticationConverter` that extracts the JWT from the `token` query parameter (not `Authorization` header) for the `/subscribe` endpoint. Verify: context starts, request without token returns 401.
- [ ] 4.3 Update `NotificationSubscriptionHandler`: replace `req.queryParamOrNull("userId")` with extraction of user id from authenticated JWT `sub` claim. Remove `userId` query param usage. Verify: integration test — `GET /subscribe?token=<valid-jwt>` streams events for the user; no token returns 401.

## 5. Test infrastructure updates

- [ ] 5.1 Update existing `chats` endpoint tests: replace `.header("X-User-Id", userId)` with mock JWT via `SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject(userId.toString()))`. Verify: existing test scenarios pass with JWT auth.
- [ ] 5.2 Update existing `messages` endpoint tests: replace `.header("X-User-Id", userId)` with mock JWT. Verify: existing test scenarios pass with JWT auth.
- [ ] 5.3 Update existing `notifications` SSE tests: replace `.uri("/subscribe?userId=$id")` with `.uri("/subscribe?token=$testToken")` using mock JWT. Verify: SSE test scenarios pass with token auth.
- [ ] 5.4 Add test: `PUT /users/{id}` by an authenticated caller for another user returns 403. **Deferred:** the ADMIN-role variant (`POST /users` without ADMIN → 403) is deferred until roles.
- [ ] 5.5 Add test: `DELETE /users/{id}` by an authenticated caller for another user returns 403. **Deferred:** ADMIN-or-self and ADMIN-only `DELETE` variants are deferred until roles.

## 6. Specs and verification

- [ ] 6.1 Verify `openspec validate --changes enforce-jwt-identity --strict` passes.
- [ ] 6.2 Sync deltas to `openspec/specs/system/auth/spec.md`, `openspec/specs/system/rest-api/spec.md`, `openspec/specs/system/architecture/spec.md`. Verify `openspec validate --specs` passes.
- [ ] 6.3 Run `.\mvnw.cmd -pl users,chats,messages,notifications -am test`. Verify: build and tests green (subject to Docker/Testcontainers availability).

## Workflow follow-up

- Archive the change after spec sync and verification.
- This change depends on `add-email-otp-auth` being implemented and archived first (JWKs endpoint must exist).