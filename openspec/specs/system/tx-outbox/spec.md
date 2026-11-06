# tx-outbox Specification

## Purpose

Define the contract of the shared transactional outbox used by `users` and `chats`: a generic `JdbcClient`-backed core with claim/lease state machine, retry, dead-letter, cleanup, and a publisher SPI, plus starter-provided per-service channels/routes configuration, a consumer-overridable route resolver, customizable event headers, default KafkaTemplate-backed publisher, dedicated scheduler, activation, and outbox metrics.

## Requirements

### Requirement: Outbox write is mandatory-transactional

The core `OutboxWriter` SHALL persist an outbox entry within the caller's active transaction. If no transaction is active when `write` is invoked, the call SHALL fail and no entry SHALL be written. The entry SHALL carry `topic`, `key`, `body` (JSONB), and `headers` (JSONB) and the core SHALL not inspect or alter them.

#### Scenario: Write inside an active transaction

- **WHEN** a service writes an outbox entry inside an active transaction that commits
- **THEN** the entry SHALL be committed in the same transaction as the aggregate change, with `body` and `headers` stored as JSONB.

#### Scenario: Write without an active transaction

- **WHEN** a service writes an outbox entry with no active transaction
- **THEN** the call SHALL fail and no entry SHALL be persisted.

### Requirement: Outbox core is domain-agnostic

The core outbox module SHALL only write entries, claim and publish them through an `OutboxPublisher`, transition them through their state machine, recover expired leases, and clean terminal records. The core SHALL NOT define or depend on message `type`, `version`, route resolution, the `X-Event-*` header conventions, or any route/header provider SPI. Those concerns SHALL live in the starter.

#### Scenario: Core forwards what it is given

- **WHEN** the relay publishes an entry
- **THEN** it SHALL send it to the entry's stored `topic` with its stored `headers` and SHALL not derive or modify them.

### Requirement: Outbox entry state lifecycle

An entry SHALL transition through states `NEW`, `CLAIMED`, `RETRY_WAIT`, `PUBLISHED`, and `DEAD`. A new entry SHALL start in `NEW`. A claimed entry SHALL move to `PUBLISHED` on publish success or `RETRY_WAIT` on publish failure. A `RETRY_WAIT` entry SHALL return to `NEW` for the next attempt until `maxAttempts` is exceeded, after which it SHALL transition to `DEAD`.

#### Scenario: New entry is claimable

- **WHEN** an entry is written
- **THEN** it SHALL start in state `NEW` and be eligible for claiming by the relay.

#### Scenario: Failed publish schedules retry

- **WHEN** publishing a claimed entry fails and the attempt count is below `maxAttempts`
- **THEN** the entry SHALL transition to `RETRY_WAIT` with a backoff-scheduled next attempt.

#### Scenario: Exceeded attempts dead-letter

- **WHEN** a `RETRY_WAIT` entry exceeds `maxAttempts`
- **THEN** it SHALL transition to `DEAD` and SHALL not be retried automatically.

### Requirement: Outbox claim is lease-guarded

The relay SHALL claim `NEW` entries using PostgreSQL row-level locking (`FOR UPDATE SKIP LOCKED`) and SHALL persist a `lease_owner` and `lease_until` on claimed rows. A claimed row SHALL not be claimed by another relay until its lease expires. Expired leases SHALL be recovered so their entries become claimable again.

#### Scenario: Concurrent claims do not collide

- **WHEN** two relay instances claim entries concurrently
- **THEN** `FOR UPDATE SKIP LOCKED` SHALL ensure each entry is claimed by at most one relay.

#### Scenario: Expired lease is recovered

- **WHEN** the relay finds a claimed entry whose `lease_until` is in the past
- **THEN** it SHALL recover the entry for re-publish.

### Requirement: Outbox retry uses exponential backoff

Retry SHALL apply exponential backoff with a configurable `initialDelay`, `multiplier`, and `maxDelay`, capped by `maxAttempts` before an entry transitions to `DEAD`. The next attempt time SHALL be persisted as `next_attempt_at` and the relay SHALL not retry before it.

#### Scenario: Backoff is respected

- **WHEN** an entry transitions to `RETRY_WAIT`
- **THEN** the relay SHALL not retry it before `next_attempt_at`.

### Requirement: Outbox terminal records are cleaned by retention

The cleaner SHALL delete `PUBLISHED` records older than `publishedRetention` and `DEAD` records older than `deadRetention`. Non-terminal records SHALL NOT be deleted.

#### Scenario: Old published record is deleted

- **WHEN** a `PUBLISHED` record is older than `publishedRetention`
- **THEN** it SHALL be deleted.

#### Scenario: Old dead record is deleted

- **WHEN** a `DEAD` record is older than `deadRetention`
- **THEN** it SHALL be deleted.

#### Scenario: Non-terminal record is retained

- **WHEN** a record is not in a terminal state
- **THEN** it SHALL NOT be deleted by cleanup.

### Requirement: Outbox publisher is a core SPI

The core SHALL define an `OutboxPublisher` SPI that the relay calls to send an entry to Kafka. The core SHALL NOT provide a default publisher implementation. The starter SHALL provide a default `KafkaTemplate`-backed publisher and SHALL allow a consuming service to override it with its own `OutboxPublisher` bean.

#### Scenario: Default publisher is used

- **WHEN** no consumer-provided `OutboxPublisher` bean exists
- **THEN** the starter SHALL publish with its default `KafkaTemplate`-backed publisher.

#### Scenario: Consumer overrides the publisher

- **WHEN** a consuming service defines its own `OutboxPublisher` bean
- **THEN** the starter SHALL use that bean and SHALL not register its default.

### Requirement: Outbox channels and routes are configurable per service

Channels (named Kafka topics) and routes (message `type` to channel, event name, and optional static headers) SHALL be supplied through per-service configuration in the starter. A message whose `type` has no configured route SHALL be rejected at emit time. One `type` SHALL map to one channel.

#### Scenario: Configured route is used

- **WHEN** the starter emitter resolves a `type` that has a configured channel and event name
- **THEN** the produced entry SHALL carry that channel's topic and an event-name header.

#### Scenario: Unknown channel reference is rejected

- **WHEN** configuration references a channel name not declared in `tx-outbox.channels`
- **THEN** the configuration SHALL fail to bind at context startup.

#### Scenario: Unmapped type is rejected

- **WHEN** the starter emitter encounters a `type` with no configured route
- **THEN** it SHALL fail to emit and SHALL not write an outbox entry.

### Requirement: Outbox routing can be overridden by the consumer

The starter SHALL provide a default configuration-backed route resolver and SHALL allow a consuming service to supply its own resolver bean to override it. The route resolver interface SHALL live in the starter.

#### Scenario: Consumer provides a custom resolver

- **WHEN** a consuming service defines a bean implementing the starter's route resolver interface
- **THEN** the starter SHALL use that bean and SHALL not register its default.

#### Scenario: Default resolver is used otherwise

- **WHEN** no consumer-provided route resolver bean exists
- **THEN** the starter SHALL register its default resolver driven by `tx-outbox.channels` and `tx-outbox.routes`.

### Requirement: Outbox event headers are customizable

The starter SHALL set event headers through a header provider. The default provider SHALL set the standard headers from `system/kafka-messaging` plus optional per-route static headers. A consuming service MAY supply its own header provider bean to override the default. The header provider interface SHALL live in the starter; the `X-Event-*` header-name constants SHALL live in `common` and the starter SHALL delegate to them.

#### Scenario: Default headers are set

- **WHEN** the default header provider builds headers for an entry
- **THEN** the entry SHALL carry the five standard headers derived from the record id, route event name, message version, publish time, and application name.

#### Scenario: Per-route static headers are merged

- **WHEN** a route declares static headers and the default provider is used
- **THEN** those headers SHALL be added on top of the standard headers, and the standard headers SHALL not be overwritten by static configuration.

#### Scenario: Consumer overrides the header provider

- **WHEN** a consuming service defines its own header provider bean
- **THEN** the starter SHALL use that bean instead of the default and SHALL not register the default.

### Requirement: Outbox storage uses JdbcClient and no Spring Data repository

The core SHALL implement outbox persistence with Spring `JdbcClient` over the consuming service's `DataSource`/`JdbcTemplate` and SHALL NOT declare a Spring Data repository or entity. The DAO SHALL generate the entry id (UUID v7) and set `created_at` itself when not supplied.

#### Scenario: Consuming service repository scanning is unaffected

- **WHEN** a service depends on `tx-outbox-starter`
- **THEN** the service's own Spring Data JDBC repositories SHALL continue to be discovered without any change to `@EnableJdbcRepositories`.

### Requirement: Outbox activation is gated

The starter SHALL activate the outbox runtime only when all of the following hold: `tx-outbox.enabled=true`; a `JdbcClient` bean is available; an `ObjectMapper` bean is available; and at least one publication capability is available, where a publication capability is a consumer-provided `OutboxPublisher` bean **or** a `KafkaTemplate` bean. The two publication capabilities SHALL be alternatives, not cumulative requirements.

The activation decision SHALL be evaluated against the bean definitions registered in the application context at bean-definition registration time, and SHALL NOT depend on the order in which auto-configurations contribute their bean definitions. The starter SHALL express its dependence on the auto-configurations that provide `JdbcClient`, `ObjectMapper`, and `KafkaTemplate` through the auto-configuration `after` attribute, and SHALL NOT rely on a fixed evaluation order or on hard-coded bean names.

When a consumer-provided `OutboxPublisher` bean exists, the starter SHALL use that bean and SHALL NOT register its default publisher. When no consumer `OutboxPublisher` exists and a `KafkaTemplate` bean is available, the starter SHALL register the default `KafkaTemplate`-backed publisher named `kafkaTemplateOutboxPublisher`.

When activation holds, the starter SHALL register the runtime composition: `kafkaTemplateOutboxPublisher` (unless a consumer publisher overrides it), `outboxRelay`, `outboxCleaner`, the dedicated scheduler named `outboxTaskScheduler`, outbox metrics, and the relay and cleanup scheduling configuration. The `outboxCleaner` SHALL be suppressed when `tx-outbox.cleanup.enabled=false`. Metrics SHALL bind to an available `MeterRegistry` and SHALL fall back to a `SimpleMeterRegistry` when none is present.

The relay and cleanup scheduling SHALL each support a fixed-delay mode and a cron mode, independently for the relay and for the cleanup. The relay SHALL default to fixed-delay mode and the cleanup SHALL default to cron mode; each default SHALL apply when the corresponding `mode` property is not set. The scheduled relay and cleanup SHALL run on the dedicated `outboxTaskScheduler`, SHALL be guarded against concurrent execution by ShedLock under the lock names `outbox-relay` and `outbox-cleanup`, and SHALL be excluded under the `test` profile. Scheduling SHALL be globally suppressible through `tx-outbox.scheduler.enabled`.

Every relay cycle SHALL perform inline recovery of expired leases and release of entries whose retry time is due before claiming new entries, within the same cycle.

When activation does not hold, the starter SHALL NOT register any runtime bean — no publisher, relay, cleaner, scheduler, metrics, or scheduling configuration. When only the publication capability is missing, the outbox write path (`OutboxDao`, `OutboxWriter`, `OutboxRouter`, `OutboxHeaderProvider`, `OutboxEmitter`) SHALL remain registered; when `JdbcClient` or `ObjectMapper` is missing, no outbox write-path or runtime bean SHALL be registered. In every inactive state the starter SHALL report the inactive runtime once at `ApplicationReadyEvent` through `OutboxActivationReporter`, with a warning naming every missing precondition among `JdbcClient`, `ObjectMapper` and the publication capability.

When `tx-outbox.enabled=false`, the starter SHALL NOT register any runtime bean.

#### Scenario: KafkaTemplate only

- **WHEN** the context has `tx-outbox.enabled=true`, a `JdbcClient` bean, an `ObjectMapper` bean, and a `KafkaTemplate` bean contributed by Kafka auto-configuration, and no consumer `OutboxPublisher`
- **THEN** the starter SHALL register exactly one `OutboxPublisher` (its default `kafkaTemplateOutboxPublisher`), one `OutboxRelay`, one `OutboxCleaner`, the `outboxTaskScheduler`, the outbox metrics, and the relay and cleanup scheduling configuration, and no inactive-runtime warning SHALL be logged.

#### Scenario: Consumer publisher with KafkaTemplate

- **WHEN** the context has `tx-outbox.enabled=true`, `JdbcClient`, `ObjectMapper`, a `KafkaTemplate`, and a consumer-provided `OutboxPublisher` bean
- **THEN** exactly one `OutboxPublisher` bean SHALL exist, it SHALL be the consumer's bean, no bean named `kafkaTemplateOutboxPublisher` SHALL exist, and `OutboxRelay` SHALL be registered.

#### Scenario: Consumer publisher without KafkaTemplate

- **WHEN** the context has `tx-outbox.enabled=true`, `JdbcClient`, `ObjectMapper`, a consumer-provided `OutboxPublisher` bean, and no `KafkaTemplate` bean
- **THEN** the starter SHALL register `OutboxRelay`, `OutboxCleaner`, `outboxTaskScheduler`, the outbox metrics, and the scheduling configuration, and exactly one `OutboxPublisher` bean SHALL exist — the consumer's, without `kafkaTemplateOutboxPublisher`.

#### Scenario: No publication capability

- **WHEN** the context has `tx-outbox.enabled=true`, `JdbcClient`, and `ObjectMapper`, but neither a `KafkaTemplate` bean nor a consumer `OutboxPublisher` bean
- **THEN** the starter SHALL register no `OutboxPublisher`, `OutboxRelay`, `OutboxCleaner`, `ThreadPoolTaskScheduler`, outbox metrics, or scheduling configuration, SHALL keep the write path (`OutboxDao`, `OutboxWriter`, `OutboxRouter`, `OutboxHeaderProvider`, `OutboxEmitter`) registered, and after `ApplicationReadyEvent` SHALL log exactly one WARN from `OutboxActivationReporter` containing `outbox runtime is inactive` and the missing capability name `OutboxPublisher`.

#### Scenario: Starter is disabled

- **WHEN** the context has `tx-outbox.enabled=false` together with `JdbcClient`, `ObjectMapper`, and a `KafkaTemplate`
- **THEN** the starter SHALL register no `OutboxPublisher`, `OutboxRelay`, `OutboxCleaner`, `outboxTaskScheduler`, outbox metrics, or scheduling configuration.

#### Scenario: Required beans missing

- **WHEN** the context has `tx-outbox.enabled=true`, `ObjectMapper`, and a `KafkaTemplate`, but no `JdbcClient`
- **THEN** the starter SHALL register neither runtime beans nor write-path beans, and after `ApplicationReadyEvent` the reporter SHALL log a warning naming `JdbcClient` as missing.

#### Scenario: ObjectMapper is missing

- **WHEN** the context has `tx-outbox.enabled=true`, `JdbcClient`, and a `KafkaTemplate`, but no `ObjectMapper`
- **THEN** the starter SHALL register neither runtime beans nor write-path beans, and after `ApplicationReadyEvent` the reporter SHALL log a warning naming `ObjectMapper` as missing.

#### Scenario: Evaluation is independent of auto-configuration ordering

- **WHEN** the same enabled context is built with the `KafkaTemplate` bean definition contributed by `KafkaAutoConfiguration` instead of a bean declared by the test or consumer, with `KafkaAutoConfiguration` and `TxOutboxAutoConfiguration` listed in either order in the imports file
- **THEN** the starter SHALL register the same runtime beans as in the `KafkaTemplate`-only case, and the inactive-runtime warning SHALL NOT be logged.

### Requirement: Outbox metrics are exposed

The starter SHALL register outbox metrics on a `MeterRegistry` when one is present, and SHALL fall back to `SimpleMeterRegistry` when it is absent. Metrics SHALL cover backlog, retry, dead, and cleanup counts.

#### Scenario: MeterRegistry present

- **WHEN** a `MeterRegistry` bean exists
- **THEN** the starter SHALL bind outbox metrics to it.

#### Scenario: MeterRegistry absent

- **WHEN** no `MeterRegistry` bean exists
- **THEN** the starter SHALL use `SimpleMeterRegistry` so metrics remain functional.

### Requirement: Outbox table schema is shipped and idempotent

The core SHALL ship a Liquibase XML changelog and a raw SQL script defining `message_outbox(id, topic, key, body JSONB, headers JSONB, created_at, published_at, state, lease_owner, lease_until, retry_count, next_attempt_at)`. The table has no `type` or `version` columns. The changelog changeSets SHALL be precondition-guarded. A consumer either includes the changelog in its Liquibase master (fresh create) or drops the transient old-schema table before including / copies the SQL as a reference.

#### Scenario: Fresh service installs the table

- **WHEN** a service applies the shipped schema and the `message_outbox` table does not exist
- **THEN** the table SHALL be created with the full column set.

#### Scenario: Existing old-schema table is replaced

- **WHEN** a service has an old `message_outbox` with `type`/`version`/`body bytea` and applies the shipped schema
- **THEN** the old table SHALL be dropped and recreated (outbox data is transient) or the SQL SHALL be copied as a reference.

#### Scenario: Existing table already matching is left as-is

- **WHEN** a service applies the shipped schema and the table already matches the shipped definition
- **THEN** no destructive change SHALL be applied.
