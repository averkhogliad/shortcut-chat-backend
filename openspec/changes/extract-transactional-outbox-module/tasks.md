# Tasks

## 1. Module scaffolding

- [x] 1.1 Create `tx-outbox/pom.xml` (parent `shortcut-chat-backend`, artifact `tx-outbox`; compile `common`, `spring-jdbc`, `jackson-databind`, `java-uuid-generator`; provided `spring-kafka`, `spring-tx`, `io.micrometer`; test deps mirroring `common-test`/Kotest/MockK + Testcontainers Postgres; `spring-boot-maven-plugin` skip=true; kotlin source dirs) and verify `.\mvnw.cmd -pl tx-outbox validate` succeeds
- [x] 1.2 Create `tx-outbox-starter/pom.xml` (parent `shortcut-chat-backend`, artifact `tx-outbox-starter`; compile `tx-outbox`; provided `spring-boot-autoconfigure`, `spring-kafka`, `shedlock-spring`, `io.micrometer`; test deps Kotest/MockK + Spring context test support; `spring-boot-maven-plugin` skip=true) and verify `.\mvnw.cmd -pl tx-outbox-starter validate` succeeds
- [x] 1.3 Add `<module>tx-outbox</module>` and `<module>tx-outbox-starter</module>` to root `pom.xml` and verify `.\mvnw.cmd -pl tx-outbox,tx-outbox-starter -am compile` succeeds with empty `src/main/kotlin`

## 2. Generic outbox core in `tx-outbox`

- [x] 2.1 Create `OutboxEntry(topic, key, body, headers, id: UUID? = null, createdAt: Instant? = null)` in `io.averkhoglyad.shortcut.outbox.core` and verify it compiles
- [x] 2.2 Create `OutboxDao` using `JdbcClient` (built from `JdbcTemplate`/`NamedParameterJdbcTemplate`): `insert(entry)` (set `state=NEW`, generate UUID v7 if no id, `created_at=now()` if no createdAt, bind `body`/`headers` as JSONB via `PGobject`), `claimBatch(owner, leaseUntil, before, limit)` (`FOR UPDATE SKIP LOCKED` on `state=NEW`, update to `CLAIMED`+lease), `findRetryable(before, limit)`, `markPublished(ids)`, `markRetry(id, nextAttemptAt, retryCount)`, `markDead(id)`, `recoverExpiredLeases(now)`, `deleteTerminal(publishedBefore, deadBefore)`; verify `.\mvnw.cmd -pl tx-outbox compile` succeeds
- [x] 2.3 Create `OutboxWriter` + impl with `@Transactional(propagation = MANDATORY) write(entry)` delegating to `OutboxDao.insert`; verify `OutboxWriterImplTest` (Kotest) asserts the DAO is called with the entry and `state=NEW`, and that write outside a transaction throws
- [x] 2.4 Create `OutboxPublisher` interface (`publish(entry): PublishResult`) in `io.averkhoglyad.shortcut.outbox.messaging`; no default impl in core; verify it compiles
- [x] 2.5 Create `OutboxRelay` + impl: per cycle run `recoverExpiredLeases`, `claimBatch` (NEW→CLAIMED), for each entry call `OutboxPublisher.publish`; on success `markPublished` (→PUBLISHED), on failure `markRetry` (→RETRY_WAIT with backoff `next_attempt_at`) unless `retry_count` exceeds `maxAttempts` then `markDead` (→DEAD); verify `OutboxRelayImplTest` (MockK) covers publish-success→PUBLISHED, publish-failure→RETRY_WAIT, and exceeded-attempts→DEAD
- [x] 2.6 Create `OutboxCleaner` + impl calling `OutboxDao.deleteTerminal` per `publishedRetention`/`deadRetention`; verify `OutboxCleanerImplTest` (MockK) captures the retentions passed to `deleteTerminal`
- [x] 2.7 Add an `OutboxDaoIntegrationTest` against Testcontainers Postgres verifying insert stores `body`/`headers` as JSONB and `state=NEW`; `claimBatch` locks rows `FOR UPDATE SKIP LOCKED` and transitions them to `CLAIMED` with `lease_owner`/`lease_until`; `markPublished`/`markRetry`/`markDead` transition states; `recoverExpiredLeases` resets expired `CLAIMED` to `NEW`; `deleteTerminal` removes only old `PUBLISHED`/`DEAD` rows

## 3. Schema artifacts in `tx-outbox` core

- [x] 3.1 Create `tx-outbox/src/main/resources/io/averkhoglyad/shortcut/outbox/migrations/message-outbox-changelog.xml` with a precondition-guarded changeSet `tx-outbox-001-create-message-outbox` creating `message_outbox(id, topic, key, body JSONB, headers JSONB, created_at, published_at, state, lease_owner, lease_until, retry_count, next_attempt_at)` (no `type`/`version`); verify the XML is well-formed
- [x] 3.2 Create `tx-outbox/src/main/resources/io/averkhoglyad/shortcut/outbox/migrations/message-outbox.sql` with the canonical `CREATE TABLE message_outbox(...)` and verify it is present as a resource

## 4. Customization and runtime in `tx-outbox-starter`

- [x] 4.1 Create `TxOutboxProperties` (`@ConfigurationProperties("tx-outbox")`) with `enabled`, `channels`, `routes` (type→channel+eventName+headers), `relay` (batchSize, maxLoopsPerRun, schedule), `lease` (owner, duration), `retry` (maxAttempts, initialDelay, multiplier, maxDelay), `cleanup` (enabled, retentions, schedule), `scheduler.enabled` and verify it binds in a Kotest property-binding test
- [x] 4.2 Create `OutboxRouter` interface and `ConfigurationBackedOutboxRouter` (default impl) resolving `type -> Route -> Channel.topic`; fail context startup if a route references an undeclared channel; register default `@ConditionalOnMissingBean(OutboxRouter::class)`; verify a unit test covers a mapped type, an undeclared channel reference rejection, and an unmapped type rejection
- [x] 4.3 Create `OutboxHeaderProvider` interface and `DefaultOutboxHeaderProvider` (default impl) setting the five standard headers plus per-route static `headers` (extra keys only, standard five not overwritten); move the `X-Event-*` constants into the starter; register default `@ConditionalOnMissingBean(OutboxHeaderProvider::class)`; verify a unit test covers default headers, per-route static merge, and standard-not-overwritten behavior
- [x] 4.4 Create `OutboxEmitter` interface and `OutboxEmitterImpl` `emit(type, version, key, bodyPayload)`: generate UUID v7 + `createdAt`, resolve route via `OutboxRouter`, build headers via `OutboxHeaderProvider`, serialize `bodyPayload` to JSON via `ObjectMapper`, build `OutboxEntry` and call core `OutboxWriter.write`; register `@ConditionalOnMissingBean(OutboxEmitter::class)`; verify an `OutboxEmitterImplTest` (MockK) captures the `OutboxEntry` passed to `OutboxWriter.write` with resolved topic/body/headers/id
- [x] 4.5 Create `KafkaTemplateOutboxPublisher` (default `OutboxPublisher`) using `KafkaTemplate<String, ByteArray>` to send `ProducerRecord(topic, key, body.toByteArray(), headers)`; register `@ConditionalOnMissingBean(OutboxPublisher::class)` and `@ConditionalOnBean(KafkaTemplate::class)`; verify a unit test (MockK) captures the produced record on publish-success and returns failure on KafkaTemplate exception
- [x] 4.6 Create `outboxTaskScheduler` bean (`ThreadPoolTaskScheduler`, pool size 2, prefix `outbox-`), registered `@ConditionalOnMissingBean(name="outboxTaskScheduler")`; verify a context test that a consumer-provided bean with that name replaces it
- [x] 4.7 Create `TxOutboxAutoConfiguration` (gated by `tx-outbox.enabled=true` + `JdbcClient`/`ObjectMapper` presence + an `OutboxPublisher` available) registering core beans (`OutboxDao`, `OutboxWriter`, `OutboxRelay`, `OutboxCleaner`) and starter beans (`OutboxRouter`, `OutboxHeaderProvider`, `OutboxEmitter`, `KafkaTemplateOutboxPublisher`, `outboxTaskScheduler`); register it via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; verify a sliced context test loads beans when enabled, registers no runtime beans when disabled or when `JdbcClient`/`ObjectMapper` missing, and that consumer `OutboxRouter`/`OutboxHeaderProvider`/`OutboxEmitter`/`OutboxPublisher` beans override defaults
- [x] 4.8 Create `TxOutboxScheduleConfig` (`@Profile("!test")`, `@ConditionalOnProperty("tx-outbox.scheduler.enabled" matchIfMissing=true)`) with `@Scheduled` relay/cleanup methods on `outboxTaskScheduler` + `@SchedulerLock`, reading `tx-outbox.relay.schedule.*` / `tx-outbox.cleanup.schedule.*`, and inline lease recovery before each relay cycle; verify the config is excluded under the `test` profile and the scheduling annotations reference the new property keys
- [x] 4.9 Bind outbox metrics (backlog, retry-wait, dead, cleanup) to `MeterRegistry` with a `SimpleMeterRegistry` fallback (`@ConditionalOnMissingBean(MeterRegistry::class)`); verify a context test binds meters when a `MeterRegistry` is present and falls back when absent

## 5. Migrate `users`

- [x] 5.1 Add `tx-outbox-starter` dependency to `users`; verify `users` Spring Data repositories still discover without any `@EnableJdbcRepositories` change
- [x] 5.2 Switch `UserService` and `users` tests from `outboxService.save(...)` to `OutboxEmitter.emit(type, version, key, bodyPayload)` (import from `io.averkhoglyad.shortcut.outbox.autoconfigure`); verify `UserServiceTest` passes (command verification uses argument capture on `emit`)
- [x] 5.3 Declare `tx-outbox.*` properties (`enabled`, channels, routes, relay, lease, retry, cleanup) for `UserCreated` and `SendCreatedUserNotification` in `users/src/main/resources/application.yaml` and remove the old `scheduler.message-outbox-handler.*` / `scheduler.message-outbox-cleaner.*` keys; verify the app context starts with the new keys
- [x] 5.4 Include the shipped `message-outbox-changelog.xml` in `users` `migrations/v1/changelog-all.xml` (or copy the SQL) with a one-time `dropTable message_outbox` changeSet before the include (outbox is transient); delete `users` duplicated `outbox/*`, `OutboxMessageEntity`, `OutboxMessageRepository`, and the outbox `@Scheduled` methods from `ScheduleConfig`; verify `users` migrations create the new `message_outbox` schema and claim/lease/retry SQL works against a Testcontainers Postgres and `.\mvnw.cmd -pl users -am test` passes

## 6. Migrate `chats`

- [x] 6.1 Add `tx-outbox-starter` dependency to `chats`; verify `chats` Spring Data repositories still discover without any `@EnableJdbcRepositories` change
- [x] 6.2 Switch `ChatService` and `chats` tests from `outboxService.save(...)` to `OutboxEmitter.emit(...)`; verify `ChatServiceTest` passes (command verification uses argument capture on `emit`)
- [x] 6.3 Declare `tx-outbox.*` properties for `ChatCreated` in `chats/src/main/resources/application.yaml` and remove the old `scheduler.message-outbox-*` keys; verify the app context starts
- [x] 6.4 Include the shipped `message-outbox-changelog.xml` in `chats` `migrations/v1/changelog-all.xml` (or copy the SQL) with a one-time `dropTable message_outbox` changeSet before the include; delete `chats` duplicated `outbox/*`, `OutboxMessageEntity`, `OutboxMessageRepository`, and the outbox `@Scheduled` methods from `ScheduleConfig`; verify `chats` migrations create the new `message_outbox` schema and `.\mvnw.cmd -pl chats -am test` passes

## 7. Docs, rules, and main spec cleanup

- [x] 7.1 Update `.agents/rules/architecture/module-boundaries.md`: remove the "`tx-outbox/` — задел на будущую библиотеку, а не модуль. Не создавай и не выделяй её в локальной задаче." line and add that `users` and `chats` depend on `common` and `tx-outbox-starter`; `tx-outbox` (generic core) and `tx-outbox-starter` are non-service library modules owning no DB; `tx-outbox` core is domain-agnostic (write/claim/publish/clean) and uses `JdbcClient` storage; customization (routing, headers, emit facade, default publisher, scheduler, metrics, activation) lives in `tx-outbox-starter`; verify the file reads consistently
- [x] 7.2 Update `openspec/specs/system/runtime-guarantees/spec.md` "Current Behavior" section: remove the "marks published without waiting for Kafka acknowledgement — known technical risk" bullet and the "Decisions about guarantees, retries, and replay are pending" bullet, since the change replaces them with the at-least-once claim/lease/retry/DEAD/lease-recovery/retention model; verify no stale "mark-without-ack" or "retry-pending" claim remains
- [x] 7.3 Note in Domain Profile (`.agents/context/backend.md`) if it currently describes per-service outbox duplication, that the outbox now lives in `tx-outbox`/`tx-outbox-starter`; verify no stale "duplicated outbox" claim remains

## 8. Integration verification

- [x] 8.1 Run `.\mvnw.cmd -pl tx-outbox,tx-outbox-starter,users,chats -am test` and confirm all four modules pass; record Docker/Testcontainers limits if the environment blocks the run
- [x] 8.2 Run `openspec validate --changes "extract-transactional-outbox-module"` and confirm the change validates

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, so the deltas reach `openspec/specs/system/architecture/spec.md` and `openspec/specs/system/runtime-guarantees/spec.md` and create `openspec/specs/system/tx-outbox/spec.md`.
- Verify the archived result.