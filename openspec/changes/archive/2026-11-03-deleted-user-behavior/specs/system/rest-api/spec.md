# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Deleted user marker in chat responses

`User` objects in `ChatDetails.members`, `ChatDetails.owner`, and `ChatListItem.owner` SHALL include an optional `deleted` boolean field. When the user is marked `deleted=true` in the local projection, the field SHALL be `true`. For active users, the field SHALL be `false` or absent. This field is additive and does not affect existing clients.

#### Scenario: Deleted user in chat details

- **WHEN** `GET /chats/{id}` returns a chat that includes a deleted user among its members
- **THEN** the deleted user's `User` object in `members` SHALL have `deleted: true`.

#### Scenario: Active user in chat details

- **WHEN** `GET /chats/{id}` returns a chat that includes an active user among its members
- **THEN** the user's `User` object in `members` SHALL have `deleted: false` or omit the `deleted` field.