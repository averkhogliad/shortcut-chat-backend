# Design

## Context

`tx-outbox-starter` auto-configures the outbox runtime for `users` and `chats`. `TxOutboxAutoConfiguration`
declares `after = [JdbcClientAutoConfiguration, JacksonAutoConfiguration, KafkaAutoConfiguration]` and
splits its beans into:

- `WritePathConfiguration` — `OutboxDao`, `OutboxWriter`, `OutboxRouter`, `OutboxHeaderProvider`,
  `OutboxEmitter`, activated by `@ConditionalOnBean(JdbcClient, ObjectMapper)` (the outbox row is
  written by the service, so it stays available even without a publisher);
- `RuntimeConfiguration` — `kafkaTemplateOutboxPublisher`, `outboxRelay`, `outboxCleaner`,
  `outboxTaskScheduler`, outbox metrics, and `@Import(TxOutboxScheduleConfig)`, activated by
  `tx-outbox.enabled=true`, `JdbcClient` + `ObjectMapper`, and `@ConditionalOnOutboxPublisher`;
- `ActivationReporterConfiguration` — `OutboxActivationReporter`, activated by `tx-outbox.enabled=true`,
  which warns at `ApplicationReadyEvent` when the runtime did not activate.

`@ConditionalOnOutboxPublisher` is a project annotation wired to `OutboxPublisherAvailableCondition`.

## Defect

Observable failure in the `users` service, taken from B1
(`.execution/add-email-otp-auth/tasks/T7/coder-r1.md`):

```text
tx-outbox is enabled but the outbox runtime is inactive: missing beans OutboxPublisher
```

with the condition report

```text
TxOutboxAutoConfiguration.RuntimeConfiguration:
- @ConditionalOnOutboxPublisher no OutboxPublisher bean and no KafkaTemplate bean are available
  (OutboxPublisherAvailableCondition)
KafkaAutoConfiguration#kafkaTemplate matched
```

and, in the real default-profile context, `kafkaTemplates=[kafkaTemplate]`, `publishers=[]`,
`relays=[]`.

Consequence: no `OutboxPublisher`, no `OutboxRelay`, no scheduling, no metrics. Rows written by
`OutboxEmitter` stay in `message_outbox` forever, so `UserCreated`/`UserUpdated`/`UserDeleted`,
`SendEmail`, and `ChatCreated` never reach Kafka. The write path works, the read path does not.

## Root cause

File: `tx-outbox-starter/src/main/kotlin/io/averkhoglyad/shortcut/outbox/autoconfigure/OutboxPublisherAvailableCondition.kt`.

Symbol: `class OutboxPublisherAvailableCondition : SpringBootCondition()`.

`SpringBootCondition` implements only `org.springframework.context.annotation.Condition`, so Spring
evaluates it in the default `ConfigurationPhase.PARSE_CONFIGURATION` phase, while configuration classes
are being parsed. At that moment the only bean definitions visible are those declared by the parsed
configuration classes themselves; the `@Bean` methods of other auto-configurations are not registered
yet. `KafkaAutoConfiguration` contributes its `kafkaTemplate` bean definition during
`ConfigurationPhase.REGISTER_BEAN`, so `beanFactory.getBeanNamesForType(KafkaTemplate::class.java)`
returns empty at parse time. The condition therefore reports `noMatch`, `RuntimeConfiguration` is
skipped, and the starter never registers the publisher, relay, cleaner, scheduler, metrics, or
scheduling.

The current starter tests missed this because they declare the `KafkaTemplate` bean themselves
(`withBean`), which makes the definition visible in `PARSE_CONFIGURATION` — the test environment
differs from a service context in exactly the dimension that matters.

## Decision

Make `OutboxPublisherAvailableCondition` a `ConfigurationCondition` and evaluate it in
`ConfigurationPhase.REGISTER_BEAN`:

```kotlin
class OutboxPublisherAvailableCondition :
    SpringBootCondition(),
    ConfigurationCondition {

    override fun getConfigurationPhase(): ConfigurationCondition.ConfigurationPhase =
        ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN
}
```

The matching logic stays unchanged: a publication capability is `OutboxPublisher` **or**
`KafkaTemplate`, checked with `getBeanNamesForType(..., allowEagerInit = true, includeNonSingletons = false)`.
`SpringBootCondition` already implements `Condition`, so `matches` is not re-implemented; the annotation
`@ConditionalOnOutboxPublisher` keeps its name, targets (`CLASS`, `FUNCTION`), retention (`RUNTIME`), and
the match/no-match diagnostic wording.

This is the same approach Spring Boot uses for its own bean-presence conditions (`OnBeanCondition`
implements `ConfigurationCondition` and declares `REGISTER_BEAN`): a condition that inspects other
bean definitions cannot be evaluated while the configuration classes are still being parsed.

## Why the evaluation is deterministic

`TxOutboxAutoConfiguration` already declares

```kotlin
@AutoConfiguration(after = [JdbcClientAutoConfiguration::class, JacksonAutoConfiguration::class,
    KafkaAutoConfiguration::class])
```

Auto-configuration ordering is resolved before configuration classes are registered, so at
`REGISTER_BEAN` the `kafkaTemplate` bean definition contributed by `KafkaAutoConfiguration` exists when
`RuntimeConfiguration` is evaluated. Determinism comes from this declared `after` attribute: it is part
of the auto-configuration contract and does not depend on the position of the two entries in
`...AutoConfiguration.imports`, nor on any bean name hard-coded in the starter. A consumer-declared
`OutboxPublisher` is a regular bean definition and is visible at the same phase, so the OR branch works
for both capabilities.

## Rejected alternatives

1. **`@ConditionalOnBean` alone.** It cannot express the required OR of `OutboxPublisher` and
   `KafkaTemplate`: with several types `@ConditionalOnBean` requires all of them (AND), and its
   `name`/`value` variants select by name, not by an alternative type. Expressing the OR would need
   two nested configurations plus duplicated bean registration, and `@ConditionalOnBean` also brings
   its own diagnostics instead of the starter's wording.
2. **`AnyNestedCondition`.** It can express the OR, but it replaces the public `@ConditionalOnOutboxPublisher`
   annotation and the `OutboxPublisherAvailableCondition` class with generated nested classes. That is a
   public API change (the annotation is part of the starter surface) and loses the existing
   match/no-match messages that the B1 diagnosis relies on.
3. **Test-side relay / test harness.** Building an `OutboxRelayImpl` in tests or adding a relay bean to
   the services makes the build green while production wiring stays broken. It hides the defect, keeps
   outbox rows unpublished, and contradicts the activation contract; the reproduction must exercise the
   real auto-configuration ordering instead.
4. **Declaring `KafkaTemplate` in the service or test configuration.** It changes the environment that
   exposes the bug and leaves the starter dependent on consumer wiring to work.

## Blast radius

- `tx-outbox-starter` is shared by `users` and `chats`; both gain the runtime they already expect. This
  is the intended effect, not a side effect.
- Runtime activation now also occurs in non-`test`-profile contexts of both services, which starts the
  relay and cleanup scheduling (`@Profile("!test")` is unchanged). Relay cycles over an empty or
  well-formed outbox are no-ops; the scheduler was always part of the intended runtime.
- No change to the write path, gating on `enabled`, `JdbcClient`, `ObjectMapper`, bean names, property
  names or defaults, topics, headers, payloads, serialization, or delivery guarantees.
- Risk of over-activation is bounded by the retained gates: without `KafkaTemplate` and without a
  consumer `OutboxPublisher`, nothing runtime-scoped is registered and the reporter still warns; with
  `tx-outbox.enabled=false`, no runtime beans are registered.
- `messages` and `notifications` are unaffected (`messages` publishes directly after commit and does
  not use the outbox).

## Rollback

The fix is a single-class behavioral change with no schema, data, configuration, or contract impact.
Reverting the condition phase restores the previous state: the starter registers no runtime beans and
outbox rows accumulate again. Nothing has to be migrated or cleaned up; rows written while the runtime
was active are already `PUBLISHED` (or terminal by retention) and are covered by the existing cleanup.
The spec delta is reverted by dropping this change before archive, or by a follow-up change after
archive.
