# Spec Delta

## MODIFIED Requirements

### Requirement: Notifications SSE endpoint

The `notifications` service SHALL expose `GET /subscribe?token=...` with `Content-Type: text/event-stream` and SHALL stream events for chats that include the user identified by the token's `sub` claim.

#### Scenario: Subscribe to notifications

- **WHEN** a client subscribes with a valid Bearer token in the `token` query parameter
- **THEN** the response SHALL stream notification events for chats that include the user identified by the token's `sub` claim.

### Requirement: Request identity

Endpoints that require a user identity SHALL read it from the `Authorization: Bearer <jwt>` header. The user id SHALL be extracted from the JWT `sub` claim. SSE subscription SHALL read a Bearer token from the `token` query parameter and extract the user id from the `sub` claim.

#### Scenario: Chat list identity

- **WHEN** `GET /chats` is called
- **THEN** the `Authorization: Bearer` header SHALL identify the requesting user via the JWT `sub` claim.

#### Scenario: Message creation identity

- **WHEN** `POST /messages` is called
- **THEN** the `Authorization: Bearer` header SHALL identify the author via the JWT `sub` claim.

#### Scenario: SSE identity

- **WHEN** `GET /subscribe` is called
- **THEN** the `token` query parameter SHALL contain a Bearer JWT whose `sub` claim identifies the subscriber.

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