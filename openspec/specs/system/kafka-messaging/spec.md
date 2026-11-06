# Kafka Messaging Specification

## Purpose

Define Kafka topics, events, headers, payloads, serialization, consumers, and idempotency for the backend services.

## Requirements

### Requirement: Topic and event inventory

The system SHALL use the topics and events listed in the inventory scenario.

#### Scenario: Event inventory

- **WHEN** an event is produced or consumed
- **THEN** the topic, producer, event name, and consumer SHALL match this inventory:

| Topic | Producer | Event name | Consumer |
|---|---|---|---|
| `MailerCommands` | `users` | `SendEmail` (outbox type `SendCreatedUserNotification`) | `mail-sender` |
| `UserLifecycle` | `users` | `UserCreated` | `chats`, `messages` |
| `UserLifecycle` | `users` | `UserUpdated` (outbox type `UserUpdated`) | `chats`, `messages` |
| `UserLifecycle` | `users` | `UserDeleted` (outbox type `UserDeleted`) | `chats`, `messages` |
| `ChatLifecycle` | `chats` | `ChatCreated` | `notifications`, `messages` |
| `ChatLifecycle` | `messages` | `MessagePublished` | `notifications` |

#### Scenario: New event type

- **WHEN** a new event type is added
- **THEN** its producer and every affected consumer SHALL be updated in the same task.

#### Scenario: User lifecycle event has consumers

- **WHEN** `users` publishes any `UserLifecycle` event
- **THEN** `chats` and `messages` SHALL consume it to maintain their local user projection.

#### Scenario: Chat lifecycle event has consumers

- **WHEN** `chats` publishes `ChatCreated`
- **THEN** `messages` SHALL consume it to maintain its local chat projection.

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

#### Scenario: User lifecycle consumer dispatch

- **WHEN** `chats` or `messages` receives a `UserLifecycle` record
- **THEN** it SHALL select the payload type from `X-Event-Name` and dispatch to the matching handler (`UserCreated`, `UserUpdated`, or `UserDeleted`).

#### Scenario: Chat lifecycle consumer dispatch

- **WHEN** `messages` receives a `ChatLifecycle` record
- **THEN** it SHALL select the payload type from `X-Event-Name` and dispatch to the matching handler (`ChatCreated`). Unknown event names SHALL be logged and ignored.

### Requirement: Event payloads

The system SHALL use the following payload structures:

| Event | Payload |
|---|---|
| `UserCreated` | `User { id, name, email }` |
| `UserUpdated` | `User { id, name, email }` |
| `UserDeleted` | `UserDeleted { id }` |
| `SendEmail` | `EmailMessage { to, subject, body }` |
| `ChatCreated` | `ChatCreatedEvent { id, name, owner, members, createdAt }` |
| `MessagePublished` | `Message { id, text, chat, author, createdAt }` |

#### Scenario: Chat created

- **WHEN** `ChatCreated` is published
- **THEN** the payload SHALL contain the chat id, name, owner, members, and creation time.

#### Scenario: User updated payload

- **WHEN** `UserUpdated` is published
- **THEN** the payload SHALL contain the user id, name, and email and SHALL NOT contain soft-delete markers.

#### Scenario: User deleted payload

- **WHEN** `UserDeleted` is published
- **THEN** the payload SHALL contain only the user id.

### Requirement: Serialization per producer

`users` and `chats` SHALL publish JSON bytes produced by the outbox serializer. `messages` SHALL publish JSON with `spring.json.use.type.headers=false`.

#### Scenario: Producer serialization

- **WHEN** `users`, `chats`, or `messages` publishes an event
- **THEN** `users` and `chats` SHALL publish JSON bytes produced by the outbox serializer and `messages` SHALL publish JSON with `spring.json.use.type.headers=false`.

### Requirement: Consumer payload type resolution

`mail-sender` SHALL consume JSON with type headers disabled. `notifications` SHALL resolve the payload type from `X-Event-Name`. `chats` and `messages` SHALL resolve the `UserLifecycle` payload type from `X-Event-Name` with JSON type headers disabled. `messages` SHALL resolve the `ChatLifecycle` payload type from `X-Event-Name` with JSON type headers disabled.

#### Scenario: Consume a message event

- **WHEN** `notifications` receives a record with `X-Event-Name=MessagePublished`
- **THEN** it SHALL deserialize the payload as `MessagePublished`.

#### Scenario: Consume a user lifecycle event

- **WHEN** `chats` or `messages` receives a `UserLifecycle` record with `X-Event-Name=UserUpdated`
- **THEN** it SHALL deserialize the payload as `UserUpdated` without using JSON type headers.

#### Scenario: Consume a chat lifecycle event

- **WHEN** `messages` receives a `ChatLifecycle` record with `X-Event-Name=ChatCreated`
- **THEN** it SHALL deserialize the payload as `ChatCreated` without using JSON type headers.

### Requirement: Idempotent mail delivery

`mail-sender` SHALL use `X-Event-Id` as the idempotency key and SHALL store delivery state in Redis with a TTL of 30 days.

#### Scenario: Repeated mail command

- **WHEN** `mail-sender` receives the same `X-Event-Id` again
- **THEN** it SHALL NOT send the email again if the stored state is `COMPLETED`.
