# Architecture Specification

## Purpose

Define module boundaries, data ownership, and interaction topology for the shortcut-chat backend.

## Requirements

### Requirement: Service modules depend only on common

The service modules `users`, `chats`, `messages`, `notifications`, and `mail-sender` SHALL depend on `common` and SHALL NOT have compile-time dependencies on each other. `common-test` SHALL be used only as a test dependency.

#### Scenario: Cross-service code reuse

- **WHEN** a change needs code from another service module
- **THEN** the change SHALL use Kafka or the approved HTTP integration instead of a compile dependency.

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


