# Spec Delta

## MODIFIED Requirements

### Requirement: Service modules depend only on common

The service modules `users`, `chats`, `messages`, `notifications`, and `mail-sender` SHALL depend on `common` and SHALL NOT have compile-time dependencies on each other. `users` and `chats` MAY additionally depend on the `tx-outbox-starter` module. `tx-outbox-starter` SHALL depend on `tx-outbox` and `common`; `tx-outbox` SHALL depend on `common`. Neither `tx-outbox` nor `tx-outbox-starter` SHALL be a service module or own a database. `common-test` SHALL be used only as a test dependency.

#### Scenario: Cross-service code reuse

- **WHEN** a change needs code from another service module
- **THEN** the change SHALL use Kafka or the approved HTTP integration instead of a compile dependency.

#### Scenario: Shared outbox code reuse

- **WHEN** `users` or `chats` needs transactional-outbox behavior
- **THEN** the change SHALL depend on `tx-outbox-starter` rather than duplicating outbox code in each service.

## ADDED Requirements

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