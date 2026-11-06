# Proposal

## Why

`users` and `chats` write outbox records for their domain events, but those records never reach Kafka
in a running service. Both services enable the outbox (`tx-outbox.enabled=true`) and have Kafka on the
classpath, so `KafkaAutoConfiguration` provides a `KafkaTemplate`; nevertheless the starter registers
no runtime beans. The `users` context reports at startup:

```text
tx-outbox is enabled but the outbox runtime is inactive: missing beans OutboxPublisher
```

and the condition report shows `TxOutboxAutoConfiguration.RuntimeConfiguration` skipped with
`no OutboxPublisher bean and no KafkaTemplate bean are available`, while
`KafkaAutoConfiguration#kafkaTemplate` matches. The outbox table therefore accumulates rows that are
never published: `UserCreated`, `UserUpdated`, `UserDeleted` (topic `UserLifecycle`), `SendEmail`
(topic `MailerCommands`), and `ChatCreated` are never delivered. The defect surfaced as blocker B1 of
the `add-email-otp-auth` cycle (`.execution/add-email-otp-auth/tasks/T7/coder-r1.md`) and is
reproducible in a plain `users` context.

The cause is a configuration-phase error in the starter, not a missing feature: the publisher
availability condition is a plain `Condition` evaluated in `PARSE_CONFIGURATION`, where the
`kafkaTemplate` bean definition contributed by `KafkaAutoConfiguration` does not exist yet.

## What Changes

- Fix the activation gate in `tx-outbox-starter`: `OutboxPublisherAvailableCondition` becomes a
  `ConfigurationCondition` evaluated in `ConfigurationPhase.REGISTER_BEAN`, keeping its matching logic
  (an `OutboxPublisher` **or** a `KafkaTemplate` is a publication capability) and its diagnostics.
- Cover the real production ordering with starter tests: the reproduction context gets its
  `KafkaTemplate` from `KafkaAutoConfiguration`, not from a test-declared bean, and the inactivity
  path is asserted through the `OutboxActivationReporter` warning.
- Describe the complete activation contract in `system/tx-outbox`: enabling conditions, evaluation
  independent of auto-configuration ordering, consumer publisher override, runtime bean composition,
  relay and cleanup scheduling, inline lease recovery, and the inactive/disabled behavior, with one
  scenario per case.
- No production change in `users` or `chats`: they receive the working runtime from the shared starter.

Out of scope: delivery-guarantee changes (no DLQ, no Kafka transactions, no exactly-once), new
properties, new topics, headers, payloads, metrics, or public starter API types, and the end-to-end
OTP verification owned by the `add-email-otp-auth` cycle.

## Capabilities

### Modified Capabilities

- `system/tx-outbox`: the activation gate is defined in full — the boolean/required-bean/publication-
  capability condition, evaluation at bean-definition registration time, consumer publisher override,
  runtime bean composition, fixed-delay and cron scheduling with the dedicated scheduler and ShedLock
  names, inline lease recovery and due-retry release per relay cycle, and the inactive/disabled paths
  with the startup warning.

No new capability is introduced.

## Impact

- **Code**: `tx-outbox-starter` — `OutboxPublisherAvailableCondition.kt` (condition phase only, public
  annotation, bean names, properties, and diagnostics preserved) and its tests. No change to
  `tx-outbox` core, `users`, `chats`, `messages`, `notifications`, `mail-sender`, `common`, or
  `common-test`.
- **Services**: `users` and `chats` start publishing the events they already write. No code,
  configuration, topic, header, or payload change; `UserLifecycle`, `MailerCommands`, and
  `ChatLifecycle` contracts stay as they are.
- **Runtime**: after the fix the relay and cleanup scheduling run in non-`test`-profile contexts of
  `users` and `chats` (existing `@Profile("!test")` design). Delivery remains at-least-once; consumers
  must stay idempotent.
- **Specs**: `openspec/specs/system/tx-outbox/spec.md`, requirement "Outbox activation is gated",
  replaced by the delta of this change when the change is archived. No other spec is affected.
- **Build**: no dependency, POM, or module change; no migration.
