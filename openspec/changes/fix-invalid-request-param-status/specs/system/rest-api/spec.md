# Spec Delta

## ADDED Requirements

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
