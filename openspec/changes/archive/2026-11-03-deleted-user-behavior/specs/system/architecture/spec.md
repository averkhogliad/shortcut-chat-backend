# Spec Delta

## ADDED Requirements

### Requirement: Chat creation rejects deleted members

`chats` SHALL reject `POST /chats` with 400 `ProblemDetail` if any member ID in the request references a user marked `deleted=true` in the local projection.

#### Scenario: Chat creation rejected with deleted member

- **WHEN** `POST /chats` is called with a member ID that references a deleted user
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`.

### Requirement: Message creation permits deleted authors

`messages` SHALL permit `POST /messages` by a deleted author without additional checks.

#### Scenario: Message creation by deleted author permitted

- **WHEN** `POST /messages` is called with `X-User-Id` referencing a deleted user
- **THEN** the message SHALL be created and `MessagePublished` SHALL be published.

### Requirement: Deleted users shown with marker in chat responses

`chats` SHALL include deleted users in `ChatDetails.members` and `ChatListItem.owner` with a `deleted: true` field on the `User` response object. Active users SHALL have `deleted: false` or omit the field.

#### Scenario: Existing chat shows deleted member with marker

- **WHEN** `GET /chats/{id}` returns a chat that includes a deleted user among its members
- **THEN** the deleted user SHALL appear in `members` with `deleted: true`.

### Requirement: GET /members filters deleted users by default

`chats` SHALL exclude deleted users from `GET /members` responses by default. An optional `includeDeleted=true` query parameter SHALL include them.

#### Scenario: GET /members excludes deleted by default

- **WHEN** `GET /members?chatId=...` is called without `includeDeleted`
- **THEN** deleted users SHALL NOT appear in the `members` collection.

#### Scenario: GET /members includes deleted with parameter

- **WHEN** `GET /members?chatId=...&includeDeleted=true` is called
- **THEN** deleted users SHALL appear in the `members` collection.

### Requirement: ChatCreatedEvent passes all member refs as-is

`ChatCreatedEvent.members` SHALL contain all member refs from the chat without filtering by deleted status.

#### Scenario: ChatCreatedEvent includes all member refs

- **WHEN** `ChatCreated` is published
- **THEN** the `members` field SHALL contain all member refs from the chat without filtering by deleted status.