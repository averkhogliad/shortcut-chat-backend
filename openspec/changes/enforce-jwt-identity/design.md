# Design

## Context

After `add-email-otp-auth`, the `users` service issues JWT access tokens (Bearer, RSA-signed, JWKs published). Other services still use the dev-only `X-User-Id` header (`chats`, `messages`) and `userId` query param (`notifications` SSE). This change replaces both with JWT-based identity.

`chats` and `messages` are Spring MVC services. `notifications` is WebFlux. `mail-sender` is a Kafka consumer with no REST surface. `users` already has Spring Security from the auth change.

## Goals / Non-Goals

**Goals:**

- All REST-facing services validate JWT via JWKs from `users` service.
- `X-User-Id` header and `userId` query param are replaced by JWT `sub` claim.
- `users` service enforces role-based access on its own endpoints (admin-only for POST/DELETE).

**Non-Goals:**

- API gateway (no gateway in the architecture).
- Token refresh from client-side (clients handle refresh themselves).
- Role-based access control in `chats` or `messages` (only `users` has admin-only endpoints).
- SSE token rotation or ticket pattern (token in query param is accepted for now).

## Decisions

### 1. Spring Security OAuth2 Resource Server (MVC services)

`chats` and `messages` add `spring-boot-starter-oauth2-resource-server`. Each service configures `SecurityFilterChain` with `oauth2ResourceServer { jwt { } }`, pointing to the `users` service issuer URI. Spring Boot auto-configures JWKs fetching from `/.well-known/openid-configuration`.

**Why:** Standard Spring Security pattern. JWKs are cached and refreshed automatically. No custom validation code.

### 2. Reactive OAuth2 Resource Server (notifications)

`notifications` adds `spring-boot-starter-oauth2-resource-server` (same dependency, reactive auto-configuration). Configures `SecurityWebFilterChain` with reactive JWT support. Token is extracted from the `token` query parameter (not `Authorization` header) via a custom `ServerAuthenticationConverter`.

**Why:** WebFlux requires reactive security. SSE via EventSource cannot send custom headers, so token comes from query param.

**Alternative:** Cookie-based auth. Rejected: requires same-origin or CORS configuration, adds complexity.

### 3. User id extraction from JWT sub

Controllers extract user id from `Authentication.principal` (JWT `sub` claim). In MVC: `@AuthenticationPrincipal Jwt jwt` → `jwt.subject` → `UUID`. In WebFlux: `ServerWebExchange` → `SecurityContextHolder` → `Jwt`.

**Why:** Standard Spring Security pattern. No custom claim parsing beyond `sub`.

### 4. SSE token in query parameter

`GET /subscribe?token=<jwt>` — the `notifications` service extracts the token from the query param, validates it as a JWT, and uses `sub` as the user id. The token is the same access token used in `Authorization: Bearer` for other services.

**Why:** EventSource API does not support custom headers. Query param is the simplest approach.

**Trade-off:** Token appears in URL (access logs, browser history). Accepted for now — access token TTL is 15 minutes. A ticket pattern can be added later if needed.

### 5. users service endpoint security

SecurityConfig chain 2 (from `add-email-otp-auth`) is updated:
- `GET /users/{id}`, `GET /users/by-email` — `authenticated()` (any valid token)
- `POST /users` — `hasRole("ADMIN")`
- `PUT /users/{id}` — `hasRole("ADMIN") or #id == authentication.principal.subject` (self-service)
- `DELETE /users/{id}` — `hasRole("ADMIN")`
- Actuator, auth endpoints — handled by chain 1 or `permitAll`

**Why:** Users can view and edit their own profile. Admin can manage all users. Consistent with REST conventions.

### 6. Endpoint authentication policy

- `chats`: all endpoints require authentication. `GET /chats` uses `sub` for filtering. `POST /chats` uses `sub` as chat owner (replaces the `owner = null // TODO` in `ChatConverter`). Other endpoints require valid token.
- `messages`: all endpoints require authentication. `POST /messages` uses `sub` as author. `GET /messages` requires authentication but not `sub` for filtering.
- `notifications`: `GET /subscribe` requires valid token in query param.

### 7. Chat owner from JWT

`POST /chats` SHALL set the chat owner to the user identified by the JWT `sub` claim. `ChatEntity.owner` is populated with `AggregateReference.to(user.id)` from the token. `ChatConverter.toListItem` and `toDetails` SHALL resolve owner from the entity instead of returning `null`. This resolves the long-standing `owner = null // TODO` gap.

**Why:** With JWT identity, the chat creator is known. The owner was always intended to be set but lacked a source — the token provides it.

## Risks / Trade-offs

- [Token in SSE URL] → Access token exposed in logs. Mitigation: short TTL (15 min). Ticket pattern is a future enhancement.
- [JWKs fetch failure] → If `users` service is down, other services cannot validate tokens and reject all requests. Mitigation: JWKs are cached by Spring Security with periodic refresh. Accepted.
- [Breaking change for existing clients] → All clients must send Bearer token instead of X-User-Id. Mitigation: clients update after auth is deployed. No backward-compatible transition period.
- [Existing tests break] → All endpoint tests that send `X-User-Id` header need to send mock JWT instead. Mitigation: use `@WithMockUser` or `SecurityMockMvcRequestPostProcessors.jwt()` for test auth.

## Open Questions

(none)