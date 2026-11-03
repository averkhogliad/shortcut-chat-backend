# Spec Delta

## ADDED Requirements

### Requirement: REST-facing services enforce JWT authentication

The REST-facing services `chats`, `messages`, and `notifications` SHALL validate incoming JWT access tokens as OAuth2 resource servers. The `users` service SHALL enforce JWT authentication on its own REST endpoints via its default security filter chain. `mail-sender` is a Kafka consumer with no REST surface and is not affected.

#### Scenario: chats service validates tokens

- **WHEN** `chats` receives a request to an authenticated endpoint
- **THEN** it SHALL validate the JWT via JWKs from the `users` service and extract the user id from `sub`.

#### Scenario: messages service validates tokens

- **WHEN** `messages` receives a request to an authenticated endpoint
- **THEN** it SHALL validate the JWT via JWKs from the `users` service and extract the user id from `sub`.

#### Scenario: notifications service validates tokens

- **WHEN** `notifications` receives a request to `GET /subscribe`
- **THEN** it SHALL validate the JWT from the `token` query parameter via JWKs from the `users` service and extract the user id from `sub`.