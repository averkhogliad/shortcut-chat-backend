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

- **WHEN** `POST /users` is called without an admin role
- **THEN** the response status SHALL be `403`.

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

The `notifications` service SHALL expose `GET /subscribe?userId=...` with `Content-Type: text/event-stream` and SHALL stream events for chats that include that user.

#### Scenario: Subscribe to notifications

- **WHEN** a client subscribes with a valid `userId`
- **THEN** the response SHALL stream notification events for chats that include that user.

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

#### Scenario: Invalid typed header or non-numeric page size

- **WHEN** a request supplies a non-UUID `X-User-Id` header or a non-numeric `pageSize`
- **THEN** the response status SHALL be `400`

### Requirement: Request identity

Endpoints that require a user identity SHALL read it from the `X-User-Id` header. SSE subscription SHALL read the user id from the `userId` query parameter.

#### Scenario: Chat list identity

- **WHEN** `GET /chats` is called
- **THEN** the `X-User-Id` header SHALL identify the requesting user.

#### Scenario: SSE identity

- **WHEN** `GET /subscribe` is called
- **THEN** the `userId` query parameter SHALL identify the subscriber.

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
