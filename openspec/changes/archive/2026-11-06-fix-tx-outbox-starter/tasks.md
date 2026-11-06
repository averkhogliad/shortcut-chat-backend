# Tasks

## 1. Change documentation and activation spec delta

- [x] 1.1 Record the change artifacts: `.openspec.yaml` (`schema: spec-driven`, creation date), `proposal.md` (Why / What Changes / Capabilities / Impact, one modified capability `system/tx-outbox`), `design.md` (defect, root cause with file and symbol, decision, determinism, rejected alternatives, blast radius, rollback). Verify: `npx --no-install openspec validate fix-tx-outbox-starter --strict --no-interactive` exits 0.
- [x] 1.2 Write `specs/system/tx-outbox/spec.md` as a `## MODIFIED Requirements` delta replacing "Outbox activation is gated" in full: activation condition (`tx-outbox.enabled=true` and `JdbcClient` and `ObjectMapper` and (`OutboxPublisher` or `KafkaTemplate`)), evaluation against registered bean definitions at bean-definition registration time independent of auto-configuration order, consumer publisher override, runtime composition, relay and cleanup scheduling, inline lease recovery and due-retry release per relay cycle, inactive behavior with the startup warning, and `enabled=false`. One scenario per case: KafkaTemplate only; consumer publisher with KafkaTemplate; consumer publisher without KafkaTemplate; no publication capability; `enabled=false`; missing `JdbcClient`; missing `ObjectMapper`; ordering independence. Verify: the requirement text covers all eight blocks and each scenario is a checkable WHEN/THEN about observable beans or logged output.
- [x] 1.3 Keep `tasks.md` aligned with this plan's T1/T2/T3 and the verification commands below; no requirement absent from the plan. Verify: the change validates strictly.

## 2. Fix the publisher-availability condition phase and cover activation with starter tests

- [x] 2.1 Add the reproduction case first: `ApplicationContextRunner` with `AutoConfigurations.of(KafkaAutoConfiguration, TxOutboxAutoConfiguration)`, `JdbcClient` and `ObjectMapper` beans, `tx-outbox.enabled=true`, `spring.kafka.bootstrap-servers=localhost:9092`; assert the default `OutboxPublisher`, one `OutboxRelay`, one `OutboxCleaner`, one `outboxTaskScheduler`, one `OutboxMetrics`, one `MeterRegistry`, and `TxOutboxScheduleConfig`. The `KafkaTemplate` must come from `KafkaAutoConfiguration`, never from `withBean`. Verify: run `.\mvnw.cmd -pl tx-outbox-starter -am test`, record the red result before the fix.
- [x] 2.2 Make `OutboxPublisherAvailableCondition` a `ConfigurationCondition` with `getConfigurationPhase() = ConfigurationPhase.REGISTER_BEAN`, keeping a `SpringBootCondition` base, the `@ConditionalOnOutboxPublisher` wiring, annotation name/targets/retention, the OR matching logic, and the diagnostic wording. Verify: the reproduction case turns green while the existing starter suite stays green.
- [x] 2.3 Cover a consumer `OutboxPublisher` with and without a `KafkaTemplate`: exactly one publisher bean (the consumer's), no `kafkaTemplateOutboxPublisher`, `OutboxRelay` registered. Verify: tests green.
- [x] 2.4 Cover the inactive path: `enabled=true`, `JdbcClient` + `ObjectMapper`, no publication capability — zero runtime beans, write path present, and `OutboxActivationReporter` emits exactly one WARN containing `outbox runtime is inactive` and `OutboxPublisher` after `ApplicationReadyEvent`. Verify: tests green; no such WARN in the active context.
- [x] 2.5 Keep the existing gates green: `enabled=false` (write path present, zero runtime beans), missing `JdbcClient`, missing `ObjectMapper` (zero write-path and runtime beans). Verify: existing cases unchanged and green.
- [x] 2.6 No public surface drift: no public type, annotation, bean name (`kafkaTemplateOutboxPublisher`, `outboxRelay`, `outboxCleaner`, `outboxTaskScheduler`, `outboxMetrics`, `outboxMeterRegistry`), or property change; no `users`/`chats`/`messages` change; no POM change. Verify: diff and bean-name assertions.
- [x] 2.7 Verify the starter module: `.\mvnw.cmd -pl tx-outbox-starter -am test` exits 0, with the red-before run of 2.1 recorded alongside.

## 3. Validate, sync the delta via archive, full module test run

- [x] 3.1 `npx --no-install openspec validate fix-tx-outbox-starter --strict --no-interactive` exits 0 before archiving.
- [x] 3.2 `.\mvnw.cmd -pl tx-outbox,tx-outbox-starter,users,chats -am test` exits 0 (Docker required for the `users` and `chats` Testcontainers tests; an unavailable Docker is a blocker, not a skipped check).
- [ ] 3.3 `npx --no-install openspec archive fix-tx-outbox-starter -y` exits 0; `openspec/changes/archive/<archive-date>-fix-tx-outbox-starter/` contains `proposal.md`, `design.md`, `tasks.md`, `.openspec.yaml`, and the delta; the active change directory is gone.
- [ ] 3.4 `openspec/specs/system/tx-outbox/spec.md` now carries the activation contract from the delta, with the Purpose section unchanged; the diff is limited to that requirement.
- [ ] 3.5 `npx --no-install openspec validate --specs --strict --no-interactive` exits 0.
- [ ] 3.6 Negative scope: no other spec, change directory, or `.agents/` file is touched; `add-email-otp-auth` and `enforce-jwt-identity` content is unchanged; no `push`, no history rewrite.

## Verification commands

```powershell
.\mvnw.cmd -pl tx-outbox-starter -am test
.\mvnw.cmd -pl tx-outbox,tx-outbox-starter,users,chats -am test
npx --no-install openspec validate fix-tx-outbox-starter --strict --no-interactive
npx --no-install openspec archive fix-tx-outbox-starter -y
npx --no-install openspec validate --specs --strict --no-interactive
```
