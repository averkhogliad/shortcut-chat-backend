# Spec Delta

## MODIFIED Requirements

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
