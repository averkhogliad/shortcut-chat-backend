# REST API Specification

## Purpose

Define the HTTP surface, response formats, pagination, errors, and identity rules for the backend services.

## Requirements

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

### Requirement: Chats REST endpoints

The `chats` service SHALL expose these endpoints:

| Method | Path | Response |
|---|---|---|
| `GET` | `/chats?pageSize=...` | `SliceResponse<ChatListItem>` |
| `GET` | `/chats?pageSize=...&lastId=...` | `SliceResponse<ChatListItem>` |
| `GET` | `/chats/{id}` | `ChatDetails` |
| `POST` | `/chats` | `ChatDetails` |
| `GET` | `/members?chatId=...` | `Collection<ChatMembers>` |
| `GET` | `/members?chatId=...&includeDeleted=true` | `Collection<ChatMembers>` |

#### Scenario: Chat list page

- **WHEN** `GET /chats` is called with `pageSize`
- **THEN** the response SHALL be `SliceResponse` with `data` and `next`.

#### Scenario: Chat creation with deleted member rejected

- **WHEN** `POST /chats` is called with a member ID referencing a deleted user
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Members list excludes deleted by default

- **WHEN** `GET /members?chatId=...` is called without `includeDeleted`
- **THEN** deleted users SHALL NOT appear in the `members` collection of any `ChatMembers` entry.

#### Scenario: Members list includes deleted with parameter

- **WHEN** `GET /members?chatId=...&includeDeleted=true` is called
- **THEN** deleted users SHALL appear in the `members` collection of the corresponding `ChatMembers` entry.

### Requirement: Deleted user marker in chat responses

`User` objects in `ChatDetails.members`, `ChatDetails.owner`, and `ChatListItem.owner` SHALL include an optional `deleted` boolean field. When the user is marked `deleted=true` in the local projection, the field SHALL be `true`. For active users, the field SHALL be `false` or absent. This field is additive and does not affect existing clients.

#### Scenario: Deleted user in chat details

- **WHEN** `GET /chats/{id}` returns a chat that includes a deleted user among its members
- **THEN** the deleted user's `User` object in `members` SHALL have `deleted: true`.

#### Scenario: Active user in chat details

- **WHEN** `GET /chats/{id}` returns a chat that includes an active user among its members
- **THEN** the user's `User` object in `members` SHALL have `deleted: false` or omit the `deleted` field.

### Requirement: Messages REST endpoints

The `messages` service SHALL expose these endpoints:

| Method | Path | Response |
|---|---|---|
| `GET` | `/messages?chat=...&pageSize=...` | `SliceResponse<Message>` |
| `GET` | `/messages?chat=...&pageSize=...&lastId=...` | `SliceResponse<Message>` |
| `POST` | `/messages` | `Message` |

#### Scenario: Message list page

- **WHEN** `GET /messages` is called with `chat` and `pageSize`
- **THEN** the response SHALL be `SliceResponse` with `data` and `next`.

### Requirement: Notifications SSE endpoint

The `notifications` service SHALL expose `GET /subscribe?token=...` with `Content-Type: text/event-stream` and SHALL stream events for chats that include the user identified by the token's `sub` claim.

#### Scenario: Subscribe to notifications

- **WHEN** a client subscribes with a valid Bearer token in the `token` query parameter
- **THEN** the response SHALL stream notification events for chats that include the user identified by the token's `sub` claim.

### Requirement: Creation returns HTTP 200

Successful creation SHALL return HTTP `200`, not `201`.

#### Scenario: Create user

- **WHEN** `POST /users` succeeds
- **THEN** the response status SHALL be `200`.

### Requirement: Pagination contract

Paginated responses SHALL use `SliceResponse { data, next }`, where `next` is the id of the last returned element, not a boolean indicating another page.

#### Scenario: Non-empty page

- **WHEN** a list endpoint returns at least one element
- **THEN** `next` SHALL contain the id of the last element.

#### Scenario: Empty page

- **WHEN** a list endpoint returns no elements
- **THEN** `next` SHALL be absent or null.

### Requirement: Error format

HTTP errors SHALL use `ProblemDetail`.

#### Scenario: Validation or lookup failure

- **WHEN** a request fails with a client-visible error
- **THEN** the response body SHALL be a `ProblemDetail`.

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

### Requirement: Auth REST endpoints

The `users` service SHALL expose these authentication endpoints:

| Method | Path | Response |
|---|---|---|
| `POST` | `/auth/code` | 202 Accepted |
| `POST` | `/oauth2/token` | OAuth2 Token Response |
| `GET` | `/.well-known/openid-configuration` | OIDC Discovery Document |
| `GET` | `/oauth2/jwks` | JWK Set |
| `GET` | `/userinfo` | UserInfo Claims |

#### Scenario: Request OTP code

- **WHEN** `POST /auth/code` is called with a valid email
- **THEN** the response status SHALL be `202 Accepted`.

#### Scenario: Request OTP code rate limited

- **WHEN** `POST /auth/code` is called for the same email more than once within 60 seconds
- **THEN** the response status SHALL be `429`.

#### Scenario: Token endpoint with email OTP grant

- **WHEN** `POST /oauth2/token` is called with `grant_type=urn:shortcut:oauth:grant-type:email-otp`, `email`, and `code`
- **THEN** the response SHALL contain `access_token`, `id_token`, `refresh_token`, `token_type=Bearer`, and `expires_in`.

#### Scenario: OIDC discovery

- **WHEN** `GET /.well-known/openid-configuration` is called
- **THEN** the response SHALL contain `issuer`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, and `grant_types_supported` including `urn:shortcut:oauth:grant-type:email-otp`.

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
