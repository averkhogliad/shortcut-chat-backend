# Kafka Messaging Specification

## Purpose

Define Kafka topics, events, headers, payloads, serialization, consumers, and idempotency for the backend services.

## Requirements

### Requirement: Topic and event inventory

The system SHALL use the following topics and events:

| Topic | Producer | Event name | Consumer |
|---|---|---|---|
| `MailerCommands` | `users` | `SendEmail` (outbox type `SendCreatedUserNotification`) | `mail-sender` |
| `UserLifecycle` | `users` | `UserCreated` | none |
| `ChatLifecycle` | `chats` | `ChatCreated` | `notifications` |
| `ChatLifecycle` | `messages` | `MessagePublished` | `notifications` |

#### Scenario: New event type

- **WHEN** a new event type is added
- **THEN** its producer and every affected consumer SHALL be updated in the same task.

### Requirement: Event headers

Every produced event SHALL carry these headers:

- `X-Event-Id`
- `X-Event-Name`
- `X-Event-Version`
- `X-Published-At`
- `X-Published-By`

#### Scenario: Consumer dispatch

- **WHEN** `notifications` receives a `ChatLifecycle` record
- **THEN** it SHALL select the payload type from `X-Event-Name`.

### Requirement: Event payloads

The system SHALL use the following payload structures:

| Event | Payload |
|---|---|
| `UserCreated` | `User { id, name, email }` |
| `SendEmail` | `EmailMessage { to, subject, body }` |
| `ChatCreated` | `ChatCreatedEvent { id, name, owner, members, createdAt }` |
| `MessagePublished` | `Message { id, text, chat, author, createdAt }` |

#### Scenario: Chat created

- **WHEN** `ChatCreated` is published
- **THEN** the payload SHALL contain the chat id, name, owner, members, and creation time.

### Requirement: Serialization per producer

`users` and `chats` SHALL publish JSON bytes produced by the outbox serializer. `messages` SHALL publish JSON with `spring.json.use.type.headers=false`. `mail-sender` SHALL consume JSON with type headers disabled. `notifications` SHALL resolve the payload type from `X-Event-Name`.

#### Scenario: Consume a message event

- **WHEN** `notifications` receives a record with `X-Event-Name=MessagePublished`
- **THEN** it SHALL deserialize the payload as `MessagePublished`.

### Requirement: Idempotent mail delivery

`mail-sender` SHALL use `X-Event-Id` as the idempotency key and SHALL store delivery state in Redis with a TTL of 30 days.

#### Scenario: Repeated mail command

- **WHEN** `mail-sender` receives the same `X-Event-Id` again
- **THEN** it SHALL NOT send the email again if the stored state is `COMPLETED`.


