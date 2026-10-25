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

#### Scenario: Unknown user

- **WHEN** `GET /users/{id}` is called for a missing user
- **THEN** the response SHALL use the `ProblemDetail` error format.

### Requirement: Chats REST endpoints

The `chats` service SHALL expose these endpoints:

| Method | Path | Response |
|---|---|---|
| `GET` | `/chats?pageSize=...` | `SliceResponse<ChatListItem>` |
| `GET` | `/chats?pageSize=...&lastId=...` | `SliceResponse<ChatListItem>` |
| `GET` | `/chats/{id}` | `ChatDetails` |
| `POST` | `/chats` | `ChatDetails` |
| `GET` | `/members?chatId=...` | `Collection<ChatMembers>` |

#### Scenario: Chat list page

- **WHEN** `GET /chats` is called with `pageSize`
- **THEN** the response SHALL be `SliceResponse` with `data` and `next`.

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

### Requirement: Request identity

Endpoints that require a user identity SHALL read it from the `X-User-Id` header. SSE subscription SHALL read the user id from the `userId` query parameter.

#### Scenario: Chat list identity

- **WHEN** `GET /chats` is called
- **THEN** the `X-User-Id` header SHALL identify the requesting user.

#### Scenario: SSE identity

- **WHEN** `GET /subscribe` is called
- **THEN** the `userId` query parameter SHALL identify the subscriber.
