# Architecture Specification

## Purpose

Define module boundaries, data ownership, and interaction topology for the shortcut-chat backend.

## Requirements

### Requirement: Service modules depend only on common

The service modules `users`, `chats`, `messages`, `notifications`, and `mail-sender` SHALL depend on `common` and SHALL NOT have compile-time dependencies on each other. `users` and `chats` MAY additionally depend on the `tx-outbox-starter` module. `tx-outbox-starter` SHALL depend on `tx-outbox` and `common`; `tx-outbox` SHALL depend on `common`. Neither `tx-outbox` nor `tx-outbox-starter` SHALL be a service module or own a database. `common-test` SHALL be used only as a test dependency.

#### Scenario: Cross-service code reuse

- **WHEN** a change needs code from another service module
- **THEN** the change SHALL use Kafka or the approved HTTP integration instead of a compile dependency.

#### Scenario: Shared outbox code reuse

- **WHEN** `users` or `chats` needs transactional-outbox behavior
- **THEN** the change SHALL depend on `tx-outbox-starter` rather than duplicating outbox code in each service.

### Requirement: Transactional outbox core module

`tx-outbox` SHALL hold a neutral, domain-agnostic outbox engine: `OutboxWriter`, `OutboxRelay`, `OutboxPublisher` SPI, `OutboxCleaner`, `OutboxDao` (`JdbcClient`), claim/lease state machine, retry/backoff, dead-letter, lease recovery, terminal cleanup, and the shipped `message_outbox` schema artifacts. It SHALL NOT contain auto-configuration, routing, or header conventions. It SHALL NOT run as a service or own a database.

#### Scenario: Core is usable without the starter

- **WHEN** a module depends only on `tx-outbox`
- **THEN** it SHALL have access to the outbox engine and storage but SHALL be responsible for its own bean wiring.

### Requirement: Transactional outbox starter module

`tx-outbox-starter` SHALL depend on `tx-outbox` and provide auto-configuration, properties, the default route resolver, the default header provider, an emit facade, the default `KafkaTemplate`-backed `OutboxPublisher`, a dedicated task scheduler, relay/cleanup scheduling, inline lease recovery, activation gating, and outbox metrics. It SHALL NOT run as a service or own a database.

#### Scenario: Consuming service applies the outbox schema

- **WHEN** `users` or `chats` includes the shipped `tx-outbox` Liquibase changelog
- **THEN** the service SHALL own and apply the `message_outbox` table in its own database.

#### Scenario: Outbox behavior is provided by the starter

- **WHEN** `users` or `chats` depends on `tx-outbox-starter` and declares `tx-outbox` routes in its configuration
- **THEN** the starter SHALL provide the outbox save, publish, and clean behavior defined in `system/tx-outbox`.

### Requirement: Each service owns its data

Each service that persists data SHALL own its database schema and SHALL NOT read or write another service's tables. Services SHALL NOT share entities, repositories, migrations, or a datasource.

#### Scenario: Cross-service data access

- **WHEN** a service needs data owned by another service
- **THEN** it SHALL receive the data through Kafka or the approved HTTP integration.

### Requirement: Kafka is the default asynchronous channel

Asynchronous interaction between services SHALL use Kafka.

#### Scenario: New asynchronous integration

- **WHEN** a change introduces an asynchronous integration
- **THEN** it SHALL define a Kafka topic and event contract in `system/kafka-messaging`.

### Requirement: Only notifications may call chats synchronously

The only approved synchronous service call SHALL be `notifications` to `chats` via `GET /members`. Any other synchronous call requires an explicit architecture decision.

#### Scenario: New synchronous call

- **WHEN** a change needs a new synchronous service call
- **THEN** it SHALL stop for an architecture decision before implementation.

### Requirement: Transactional outbox for users and chats

`users` and `chats` SHALL write outbox records in the same transaction as the aggregate change.

#### Scenario: Aggregate change with an event

- **WHEN** `users` or `chats` changes an aggregate that produces an event
- **THEN** the aggregate change and the outbox record SHALL be committed in one transaction.

### Requirement: User projection synchronization in chats and messages

`chats` and `messages` SHALL maintain a local projection of users by consuming `UserLifecycle` events (`UserCreated`, `UserUpdated`, `UserDeleted`) from `users`. The projection SHALL be the only source of user data used by the service for resolving members, owners, and authors; the service SHALL NOT read the `users` service database.

#### Scenario: Chat created with real users

- **WHEN** `chats` creates a chat whose member ids reference users already published via `UserCreated`
- **THEN** the local `users` projection SHALL contain those users so the chat and member rows satisfy foreign-key constraints.

#### Scenario: Message published with real author

- **WHEN** `messages` publishes a message whose author id references a user already published via `UserCreated`
- **THEN** the local `users` projection SHALL contain that user so the message row satisfies its foreign-key constraint.

#### Scenario: User updated propagates to projections

- **WHEN** `users` publishes `UserUpdated`
- **THEN** `chats` and `messages` SHALL update the corresponding local projection row.

#### Scenario: User deleted propagates to projections

- **WHEN** `users` publishes `UserDeleted`
- **THEN** `chats` and `messages` SHALL remove or mark the corresponding local projection row and SHALL NOT retain stale user data for new aggregates.

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
