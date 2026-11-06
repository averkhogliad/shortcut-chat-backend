# Spec Delta

## MODIFIED Requirements

### Requirement: Users REST endpoints

The `users` service SHALL expose these endpoints:

| Method | Path | Response |
|---|---|---|
| `GET` | `/users/{id}` | `User` |
| `GET` | `/users/by-email?email=...` | `User` |
| `POST` | `/users` | `User` |
| `PUT` | `/users/{id}` | `User` |
| `DELETE` | `/users/{id}` | 204 No Content |

The access policy for these endpoints SHALL follow the `User management endpoint access policy` requirement. Self-service operations SHALL use the `Self-service user profile endpoints` requirement.

#### Scenario: Unknown user

- **WHEN** `GET /users/{id}` is called for a missing user
- **THEN** the response SHALL use the `ProblemDetail` error format.

#### Scenario: Delete user

- **WHEN** `DELETE /users/{id}` succeeds
- **THEN** the response status SHALL be `204`.

#### Scenario: Delete unknown user

- **WHEN** `DELETE /users/{id}` is called for a missing user
- **THEN** the response status SHALL be `404`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Email is immutable

- **WHEN** `PUT /users/{id}` is called with an `email` field that differs from the existing user's email
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: POST /users requires admin role

This scenario is **deferred until roles**. The main specification states it today, but it cannot be implemented in this cycle: access tokens carry no `roles` claim, and the current `users` API chain applies `permitAll`. This change replaces `permitAll` with `authenticated()` only.

- **WHEN** `POST /users` is called without an admin role
- **THEN** this cycle SHALL accept the caller when it presents a valid token and SHALL NOT require a `roles` claim
- **AND** the `403` admin-only behaviour SHALL remain deferred until a separate roles change.

### Requirement: Notifications SSE endpoint

The `notifications` service SHALL expose `GET /subscribe?token=...` with `Content-Type: text/event-stream` and SHALL stream events for chats that include the user identified by the token's `sub` claim.

#### Scenario: Subscribe to notifications

- **WHEN** a client subscribes with a valid Bearer token in the `token` query parameter
- **THEN** the response SHALL stream notification events for chats that include the user identified by the token's `sub` claim.

### Requirement: Request identity

Endpoints that require a user identity SHALL read it from the `Authorization: Bearer <jwt>` header. The user id SHALL be extracted from the JWT `sub` claim. SSE subscription SHALL read a Bearer token from the `token` query parameter and extract the user id from the `sub` claim. The `X-User-Id` header and the `userId` query parameter SHALL NOT establish identity.

#### Scenario: Chat list identity

- **WHEN** `GET /chats` is called
- **THEN** the `Authorization: Bearer` header SHALL identify the requesting user via the JWT `sub` claim.

#### Scenario: Message creation identity

- **WHEN** `POST /messages` is called
- **THEN** the `Authorization: Bearer` header SHALL identify the author via the JWT `sub` claim.

#### Scenario: SSE identity

- **WHEN** `GET /subscribe` is called
- **THEN** the `token` query parameter SHALL contain a Bearer JWT whose `sub` claim identifies the subscriber.

#### Scenario: Dev identity header is ignored

- **WHEN** `GET /chats` is called with an `X-User-Id` header but without a valid Bearer token
- **THEN** the response status SHALL be `401`
- **AND** the header value SHALL NOT establish identity.

### Requirement: Malformed typed request parameter

When a request parameter, path variable, or header cannot be converted to its declared type, the service SHALL reject the request with HTTP `400 Bad Request` and a `ProblemDetail` response body.

#### Scenario: Invalid UUID in query parameter

- **WHEN** `GET /members?chatId=invalid-uuid` is called
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`

#### Scenario: Invalid UUID in path variable

- **WHEN** `GET /users/{id}` or `GET /chats/{id}` is called with a non-UUID `id`
- **THEN** the response status SHALL be `400`

#### Scenario: Invalid non-numeric page size

- **WHEN** a request supplies a non-numeric `pageSize`
- **THEN** the response status SHALL be `400`

#### Scenario: Invalid typed header or non-numeric page size

- **WHEN** a request supplies a non-numeric `pageSize` or a malformed typed request header
- **THEN** the response status SHALL be `400`

## ADDED Requirements

### Requirement: Self-service user profile endpoints

The `users` service SHALL expose `GET /users/me`, `PUT /users/me`, and `DELETE /users/me`. These endpoints SHALL identify the caller only from the JWT `sub` claim and SHALL NOT accept a user id in the path or body. `PUT /users/me` SHALL keep the email immutable. The service SHALL NOT expose a separate REST registration endpoint.

#### Scenario: Get own profile

- **WHEN** `GET /users/me` is called with a valid Bearer token
- **THEN** the response status SHALL be `200`
- **AND** the returned `User.id` SHALL equal the token's `sub` claim.

#### Scenario: Update own profile

- **WHEN** `PUT /users/me` is called with a valid Bearer token and an unchanged email
- **THEN** the profile identified by `sub` SHALL be updated and returned with `200`.

#### Scenario: Email remains immutable

- **WHEN** `PUT /users/me` is called with an email different from the caller's stored email
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`
- **AND** no user data SHALL change.

#### Scenario: Delete own profile

- **WHEN** `DELETE /users/me` is called with a valid Bearer token
- **THEN** the caller's profile SHALL be deleted and the response status SHALL be `204`.

#### Scenario: Self endpoints require a token

- **WHEN** `GET`, `PUT`, or `DELETE /users/me` is called without a valid Bearer token
- **THEN** the response status SHALL be `401`.

### Requirement: User management endpoint access policy

Management endpoints SHALL keep their current paths under an interim policy that is deferred until roles exist. `GET /users/{id}`, `GET /users/by-email`, and `POST /users` SHALL require authentication only. `PUT /users/{id}` and `DELETE /users/{id}` SHALL allow only the caller whose JWT `sub` equals the path id. This cycle SHALL NOT introduce a `roles` claim, `hasRole` checks, or ADMIN bypass; admin-only behaviour is deferred until roles.

#### Scenario: Authenticated read of any user

- **WHEN** `GET /users/{id}` or `GET /users/by-email` is called with a valid Bearer token
- **THEN** the response status SHALL be `200` or `404` per the existing lookup semantics, regardless of whether the id equals `sub`.

#### Scenario: POST /users accepts any authenticated caller

- **WHEN** `POST /users` is called with a valid Bearer token that has no roles claim
- **THEN** the response status SHALL be `200`
- **AND** no role check SHALL be applied in this cycle.

#### Scenario: Update another user is forbidden

- **WHEN** `PUT /users/{id}` is called with a valid Bearer token whose `sub` differs from `id`
- **THEN** the response status SHALL be `403`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Delete another user is forbidden

- **WHEN** `DELETE /users/{id}` is called with a valid Bearer token whose `sub` differs from `id`
- **THEN** the response status SHALL be `403`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Self operation on missing user

- **WHEN** `PUT /users/{id}` or `DELETE /users/{id}` is called with `id` equal to `sub` and no stored user exists for that id
- **THEN** the response status SHALL be `404`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Management endpoints require authentication

- **WHEN** any management endpoint is called without a valid Bearer token
- **THEN** the response status SHALL be `401`.

### Requirement: Public OTP endpoints

The `users` service SHALL keep `POST /auth/code`, the `/oauth2/**` authorization-server endpoints, and the `/.well-known/**` metadata endpoints accessible without a Bearer token even when the API security chain requires authentication for `/users/**`. The email OTP login flow SHALL NOT require an access token.

#### Scenario: OTP request without token

- **WHEN** `POST /auth/code` is called without a Bearer token
- **THEN** the response status SHALL be `202`.

#### Scenario: Token grant without access token

- **WHEN** `POST /oauth2/token` is called without a Bearer token
- **THEN** the email OTP grant SHALL be processed and the response status SHALL NOT be `401`.

#### Scenario: Discovery without token

- **WHEN** `GET /.well-known/openid-configuration` is called without a Bearer token
- **THEN** the response status SHALL be `200` with the discovery document.
