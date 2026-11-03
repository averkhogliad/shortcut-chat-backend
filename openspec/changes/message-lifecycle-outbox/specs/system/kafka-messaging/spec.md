# Spec Delta

## MODIFIED Requirements

### Requirement: Serialization per producer

`users`, `chats`, and `messages` SHALL publish JSON bytes produced by the outbox serializer. `mail-sender` SHALL consume JSON with type headers disabled. `notifications` SHALL resolve the payload type from `X-Event-Name`. `chats` and `messages` SHALL resolve the `UserLifecycle` payload type from `X-Event-Name` with JSON type headers disabled.

#### Scenario: Consume a message event

- **WHEN** `notifications` receives a record with `X-Event-Name=MessagePublished`
- **THEN** it SHALL deserialize the payload as `MessagePublished`.

#### Scenario: Consume a user lifecycle event

- **WHEN** `chats` or `messages` receives a `UserLifecycle` record with `X-Event-Name=UserUpdated`
- **THEN** it SHALL deserialize the payload as `UserUpdated` without using JSON type headers.

#### Scenario: Message published through outbox

- **WHEN** `messages` publishes `MessagePublished`
- **THEN** it SHALL publish JSON bytes produced by the outbox serializer in the same transaction as the message aggregate change.