# Design

## Context

`users` and `chats` each duplicate the transactional-outbox machinery under their own `<service>/outbox` package plus a per-service `OutboxMessageEntity` and `OutboxMessageRepository` (Spring Data JDBC). The duplicates have drifted:

- `chats`: `OutboxMessage<B>` has `key: String?`, `OutboxService.save` is `@Transactional(propagation = MANDATORY)`, `ProducerRecord` includes `key`, header helper is nullable-safe.
- `users`: no `key`, `@Transactional` (default `REQUIRED`), method named `saveMessage`, different hardcoded `when` topic/event map.

Both schedule the handler/cleaner in a per-service `ScheduleConfig` (`@Scheduled` + `@SchedulerLock`, `@Profile("!test")`) and read `scheduler.message-outbox-handler.*` / `scheduler.message-outbox-cleaner.*` from `application.yaml`. Persistence: Spring Data JDBC, PostgreSQL 14, Liquibase XML migrations, UUID v7 via each service's `IdGenerationBeforeConvertCallback` bean. The design follows the `mp-investment-money-box-core-outbox` library + starter READMEs and implementation report: claim/lease state machine, retry/backoff, dead-letter, cleanup retentions, lease recovery, publisher SPI with a default KafkaTemplate-backed implementation, dedicated task scheduler, and outbox metrics — narrowed to our conventions (`topic`/`headers`/`body` JSONB model, `OutboxEmitter` routing/headers customization in the starter, no `type`/`version`/`kind`/`entity_id`/fast-path). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**

- Two Maven modules: `tx-outbox` (generic, domain-agnostic core library) and `tx-outbox-starter` (customization + auto-configuration).
- Core = `OutboxWriter` (write), `OutboxRelay` (claim/publish/retry/DEAD), `OutboxPublisher` SPI (transport), `OutboxCleaner` (terminal cleanup), `OutboxDao` (`JdbcClient`), state machine `NEW/CLAIMED/RETRY_WAIT/PUBLISHED/DEAD`, claim/lease `FOR UPDATE SKIP LOCKED` + `lease_owner`/`lease_until`, retry exponential backoff + `maxAttempts` → `DEAD`, lease recovery. No `type`/`version`, no routing, no header conventions, no `OutboxRouter`/`OutboxHeaderProvider` in core.
- Starter = all customization and runtime wiring: `TxOutboxProperties`, `OutboxRouter`, `OutboxHeaderProvider`, `OutboxEmitter` facade, default `KafkaTemplateOutboxPublisher`, dedicated `outboxTaskScheduler`, relay/cleanup scheduling, inline lease recovery, activation gating, outbox metrics.
- `users` and `chats` consume the starter and call `OutboxEmitter.emit(...)`; duplicated outbox code deleted.
- `body`/`headers` stored as JSONB; unify on `chats` `key` + `MANDATORY` semantics.

**Non-Goals:**

- No fast-path / post-commit wake-up.
- No manual requeue/replay admin operations (the report lists requeue as open; internal retry + DEAD covers failure handling).
- No change to `messages` (direct publish, no outbox).
- No change to Kafka topics, event payloads, or the set of standard headers (see `system/kafka-messaging`).
- No extraction of `shedlock` table or `LockProvider` (stays per service).
- No removal of existing per-service Liquibase migration files (immutable history).

## Decisions

### D1: Split into `tx-outbox` core and `tx-outbox-starter`

- `tx-outbox` (core) — Spring-neutral outbox library, no auto-configuration, no domain conventions: `OutboxEntry`, `OutboxWriter` + impl, `OutboxRelay` + impl, `OutboxPublisher` SPI (interface only), `OutboxCleaner` + impl, `OutboxDao` (`JdbcClient`), and the shipped schema artifacts (SQL + Liquibase). Core classes are plain (no `@Component`/`@Service`); the starter registers them as beans.
- `tx-outbox-starter` — depends on `tx-outbox`; provides `TxOutboxAutoConfiguration` (registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), `TxOutboxProperties`, `OutboxRouter` + default impl, `OutboxHeaderProvider` + default impl, `OutboxEmitter` + impl, `KafkaTemplateOutboxPublisher` (default `OutboxPublisher`), `TxOutboxScheduleConfig`, `outboxTaskScheduler`, lease recovery, activation gating, metrics binding, and the `X-Event-*` header constants.

### D2: Packages, artifacts, dependencies

- `tx-outbox` (core): Kotlin package `io.averkhoglyad.shortcut.outbox` (sub-packages `core`, `persistence`, `messaging`). Compile: `common`, `spring-jdbc` (`JdbcClient`), `jackson-databind`, `java-uuid-generator` (UUID v7). Provided: `spring-kafka` (relay uses `KafkaTemplate`/`ProducerRecord`/`Headers` via the `OutboxPublisher` default in starter; core only defines the SPI), `spring-tx` (`@Transactional`), `io.micrometer` (metrics interface; provided). No `spring-data-jdbc`, no `spring-boot-autoconfigure`. `spring-boot-maven-plugin` skipped.
- `tx-outbox-starter`: Kotlin package `io.averkhoglyad.shortcut.outbox.autoconfigure`. Compile: `tx-outbox`. Provided: `spring-boot-autoconfigure`, `spring-kafka`, `shedlock-spring`, `io.micrometer` (impl). Holds `TxOutboxProperties`, `OutboxRouter` + `ConfigurationBackedOutboxRouter`, `OutboxHeaderProvider` + `DefaultOutboxHeaderProvider`, `OutboxEmitter` + `OutboxEmitterImpl`, `KafkaTemplateOutboxPublisher`, `TxOutboxAutoConfiguration`, `TxOutboxScheduleConfig`, lease recovery, and `X-Event-*` constants. `spring-boot-maven-plugin` skipped.

Both mirror `common`'s `skip=true` pattern; neither is a runnable app.

### D3: `JdbcClient` DAO, generic `OutboxEntry`

`OutboxEntry(topic: String, key: String?, body: String, headers: Map<String,String>, id: UUID? = null, createdAt: Instant? = null)` — fully resolved send instruction; `body` is the JSON string stored as JSONB and forwarded as raw JSON bytes; `headers` is the map of header name -> string value. The entry may carry a pre-assigned `id` and `createdAt` so the starter can build headers that include the id before write.

`OutboxDao` (`JdbcClient`, built from the consuming service's `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource` — all already present; `JdbcTemplate` is used by ShedLock's `JdbcTemplateLockProvider`). PostgreSQL-specific SQL. The DAO owns:

- `insert(entry)` — uses `entry.id` if provided, else generates UUID v7; sets `created_at = now()` if not provided; binds `body`/`headers` as JSONB (`PGobject` type `jsonb`); `state = NEW`, `published_at = null`, `lease_owner = null`, `lease_until = null`, `retry_count = 0`, `next_attempt_at = null`.
- `claimBatch(owner, leaseUntil, before, limit)` — `SELECT ... FOR UPDATE SKIP LOCKED WHERE state = NEW AND created_at < :before LIMIT :limit`, then `UPDATE SET state = CLAIMED, lease_owner, lease_until WHERE id IN (...)`.
- `findRetryable(before, limit)` — `SELECT ... WHERE state = RETRY_WAIT AND next_attempt_at <= :before LIMIT :limit` (claimed similarly leased).
- `markPublished(ids)` — `UPDATE SET state = PUBLISHED, published_at = now(), lease_owner = null, lease_until = null WHERE id IN (:ids)`.
- `markRetry(id, nextAttemptAt, retryCount)` — `UPDATE SET state = RETRY_WAIT, next_attempt_at, retry_count WHERE id = :id`.
- `markDead(id)` — `UPDATE SET state = DEAD, lease_owner = null, lease_until = null WHERE id = :id`.
- `recoverExpiredLeases(now)` — `UPDATE SET state = NEW, lease_owner = null, lease_until = null WHERE state = CLAIMED AND lease_until < :now`.
- `deleteTerminal(retention)` — `DELETE WHERE (state = PUBLISHED AND published_at < :publishedBefore) OR (state = DEAD AND created_at < :deadBefore)`.

No `@EnableJdbcRepositories` change in services; no entity; the Spring Data `IdGenerationBeforeConvertCallback` and `@EnableJdbcAuditing` no longer apply to outbox rows.

**Alternative considered:** keep a Spring Data `Repository`. Rejected by the user — a repository adds no value over `JdbcClient` and reintroduces the Boot auto-configuration back-off caveat.

### D3a: JSONB binding

`JdbcClient` has no built-in JSONB type handler. The DAO binds `body`/`headers` JSON strings as `PGobject`(`type="jsonb"`) via `SqlParameterValue`/`StatementCallback`, or a small `Jsonb` argument wrapper. Reading back uses `String`; the publisher forwards raw JSON bytes to Kafka (the wire format is already JSON per `system/kafka-messaging`). No ORM-level JSON mapping is needed.

### D4: Core `OutboxWriter`, `OutboxRelay`, state machine

`OutboxWriter.write(entry)` annotated `@Transactional(propagation = MANDATORY)` — inserts via the DAO with `state = NEW`.

`OutboxRelay` runs a cycle: `claimBatch` (NEW → CLAIMED, leased), for each entry call `OutboxPublisher.publish(entry)`; on success `markPublished` (CLAIMED → PUBLISHED), on failure `markRetry` (CLAIMED → RETRY_WAIT with backoff `next_attempt_at`) unless `retry_count` exceeds `maxAttempts` then `markDead` (→ DEAD). Before each cycle, `recoverExpiredLeases` (CLAIMED with expired `lease_until` → NEW). The relay is concrete in core; the starter registers its bean and scheduling.

`OutboxCleaner` runs `deleteTerminal` per retention. Concrete in core; starter registers + schedules.

State machine is enforced in the DAO SQL transitions; the relay drives it. No fast-path.

### D5: `OutboxPublisher` SPI

`OutboxPublisher` interface in core: `publish(entry): PublishResult` (success/failure). The relay calls it and branches on the result. The core provides NO default implementation. The starter provides `KafkaTemplateOutboxPublisher` (default) using `KafkaTemplate<String, ByteArray>`, sending `ProducerRecord(topic, key, body.toByteArray(), headers)`. Registered `@ConditionalOnMissingBean(OutboxPublisher::class)` so a consumer can override (e.g., a `StreamBridge`-compat publisher, a custom transport). This keeps business code dependent on logical `topic`/`headers` while transport binding stays an application concern.

### D6: Customization lives in the starter

`TxOutboxProperties` (`tx-outbox.*`):
- `enabled: Boolean`
- `channels: List<Channel>{ name, topic }`
- `routes: List<Route>{ type, channel, eventName, headers: Map<String,String>? }`
- `relay { batchSize, maxLoopsPerRun, schedule { mode, fixedDelay, initialDelay, cron, zone } }`
- `lease { owner, duration }`
- `retry { maxAttempts, initialDelay, multiplier, maxDelay }`
- `cleanup { enabled, publishedRetention, deadRetention, schedule { mode, fixedDelay, cron, zone } }`
- `scheduler { enabled }`

Starter beans (all `@ConditionalOnMissingBean`):
- `OutboxRouter` (interface in starter) — `type -> Route -> Channel.topic`. Default `ConfigurationBackedOutboxRouter` built from properties; fails fast at startup if a route references an undeclared channel.
- `OutboxHeaderProvider` (interface in starter) — `headersFor(type, version, route, entryId, now, applicationName): Map<String,String>`. Default `DefaultOutboxHeaderProvider` sets the five standard headers (`X-Event-Id`=entryId, `X-Event-Name`=route.eventName, `X-Event-Version`=version, `X-Published-At`=now, `X-Published-By`=applicationName) and merges per-route static `headers` (extra keys only; the standard five are not overwritten by static config, to keep `system/kafka-messaging` compliance).
- `OutboxEmitter` (interface in starter) — `emit(type, version, key, bodyPayload)`. Default `OutboxEmitterImpl`: generate UUID v7 + `createdAt`, resolve route via `OutboxRouter`, build headers via `OutboxHeaderProvider`, serialize `bodyPayload` to JSON via `ObjectMapper`, build `OutboxEntry(topic, key, body, headers, id, createdAt)`, call core `OutboxWriter.write`. This is the only bean `users`/`chats` inject for outbox writes.
- `KafkaTemplateOutboxPublisher` (default `OutboxPublisher`) — described in D5.
- `outboxTaskScheduler` — dedicated `ThreadPoolTaskScheduler` (pool size 2, prefix `outbox-`), registered `@ConditionalOnMissingBean(name="outboxTaskScheduler")` so a consumer can replace it. Relay and cleanup schedules run on it.
- Lease recovery runs inline before each relay cycle.

`users` example:
```yaml
tx-outbox:
  enabled: true
  channels:
    - name: user-lifecycle
      topic: UserLifecycle
    - name: mailer-commands
      topic: MailerCommands
  routes:
    - type: UserCreated
      channel: user-lifecycle
      event-name: UserCreated
    - type: SendCreatedUserNotification
      channel: mailer-commands
      event-name: SendEmail
  relay:
    batch-size: 100
    max-loops-per-run: 10
    schedule:
      mode: fixed-delay
      fixed-delay: PT5S
      initial-delay: PT1S
  lease:
    duration: PT30S
  retry:
    max-attempts: 10
    initial-delay: PT5S
    multiplier: 2
    max-delay: PT5M
  cleanup:
    enabled: true
    published-retention: P7D
    dead-retention: P30D
    schedule:
      mode: cron
      cron: "0 */30 * * * *"
```

### D7: Scheduling and lease recovery in the starter

`TxOutboxScheduleConfig` (auto-configured, `@Profile("!test")`, `@ConditionalOnProperty("tx-outbox.scheduler.enabled" havingValue="true" matchIfMissing="true")`) defines `@Scheduled` relay and cleanup methods on the `outboxTaskScheduler`, with `@SchedulerLock` (relay/cleanup lock names), reading `tx-outbox.relay.schedule.*` and `tx-outbox.cleanup.schedule.*`. Lease recovery runs inline at the start of each relay invocation. It depends on a `LockProvider` bean, which each service still provides in its own `ScheduleConfig` (along with `@EnableScheduling` and `@EnableSchedulerLock`). Services delete the two outbox `@Scheduled` methods and the `MessageOutboxHandler`/`MessageOutboxCleaner` constructor deps from `ScheduleConfig`; the `lockProvider` bean and ShedLock table migrations stay per service.

Property namespace migrates from `scheduler.message-outbox-handler.*` / `scheduler.message-outbox-cleaner.*` to `tx-outbox.relay.*` / `tx-outbox.cleanup.*`. `scheduler.default-lock.*` stays.

### D8: Activation gating and metrics

The starter activates only when `tx-outbox.enabled=true` and `JdbcClient`/`ObjectMapper` are present and an `OutboxPublisher` is available (default or consumer-provided). When disabled or a required bean is missing, it logs a warning and registers no runtime beans. `OutboxWriter`/`OutboxEmitter` (write path) can remain available even with relay disabled, so writes still persist and wait for the relay to be enabled — but to keep it simple, gate the whole runtime (relay+cleanup+scheduling+publisher) on `enabled`; writes via `OutboxEmitter` still work because they only call `OutboxWriter.write` (core bean registered unconditionally). Document this: write path is always available; relay/cleanup only when enabled.

Metrics: the starter binds outbox meters (backlog, retry-wait, dead, cleanup counts) to a `MeterRegistry` when present, else `SimpleMeterRegistry`. Registered `@ConditionalOnMissingBean(MeterRegistry::class)` with the fallback.

### D9: Event headers via `OutboxHeaderProvider` SPI

The `OutboxHeaderProvider` interface lives in `tx-outbox-starter`: `headersFor(type, version, route, entryId, now, applicationName): Map<String, String>`. The `OutboxEmitter` (starter) calls it while building the `OutboxEntry` before `OutboxWriter.write`. The default `DefaultOutboxHeaderProvider` sets the five standard headers and merges per-route static `headers` (extra keys only; standard five not overwritten). A consumer-provided `OutboxHeaderProvider` bean fully replaces the default. The `X-Event-*` constants live in the starter (our convention, not in core).

### D10: Schema artifacts shipped in `tx-outbox` core resources

Location: `tx-outbox/src/main/resources/io/averkhoglyad/shortcut/outbox/migrations/`.

- `message-outbox-changelog.xml` — Liquibase changelog with a precondition-guarded changeSet:
  - `tx-outbox-001-create-message-outbox` — `createTable message_outbox(id, topic, key, body JSONB, headers JSONB, created_at, published_at, state, lease_owner, lease_until, retry_count, next_attempt_at)` guarded by `<preConditions onFail="MARK_RAN"><not><tableExists tableName="message_outbox"/></not></preConditions>`.
- `message-outbox.sql` — raw canonical `CREATE TABLE message_outbox(...)` for reference/manual provisioning.

The table has no `type`/`version` columns. A consuming service either includes the changelog in its `migrations/v1/changelog-all.xml`:
```xml
<include file="classpath:io/averkhoglyad/shortcut/outbox/migrations/message-outbox-changelog.xml"
         relativeToChangelogFile="false"/>
```
or copies `message-outbox.sql` as a reference. For an existing old-schema `message_outbox(id, type, version, key, body bytea, created_at, published_at)`, the service drops the transient table before including the changelog (outbox data is transient; no in-place column migration is provided). Existing per-service `002__`/`004__Create-MessageOutbox-table.xml` files remain as history. ChangeSet ids are `tx-outbox-*`.

### D11: Tests split with the modules

Core unit tests (`OutboxWriterImplTest`, `OutboxRelayImplTest`, `OutboxCleanerImplTest`) and the `OutboxDao` integration test (Testcontainers Postgres: insert stores `body`/`headers` as JSONB and `state=NEW`; claimBatch locks `FOR UPDATE SKIP LOCKED` and sets `CLAIMED`+lease; markPublished/markRetry/markDead transitions; recoverExpiredLeases; deleteTerminal retentions) live in `tx-outbox/src/test`. Starter tests (`TxOutboxProperties` binding, `ConfigurationBackedOutboxRouter` resolution, `DefaultOutboxHeaderProvider` header merging, `OutboxEmitter` build+write, `KafkaTemplateOutboxPublisher`, `TxOutboxAutoConfiguration` activation gating + sliced context, `TxOutboxScheduleConfig` profile exclusion, lease recovery, metrics binding, consumer `OutboxRouter`/`OutboxHeaderProvider`/`OutboxEmitter`/`OutboxPublisher` overrides default) live in `tx-outbox-starter/src/test`. `ChatServiceTest` and `UserServiceTest` switch to mocking `OutboxEmitter`; per the project mock-query-vs-command rule, `emit` (command) is verified with argument capture.

## Risks / Trade-offs

- **Dropping `type`/`version` columns** loses the old routing info on existing rows. Mitigation: outbox is transient and cleaned by retention; the service drops the old table and lets the shipped changelog recreate it. Accepted by the user.
- **`FOR UPDATE SKIP LOCKED` + lease** is PostgreSQL-specific; portability to another RDBMS would need a different claim strategy. Mitigation: the project standardizes on PostgreSQL 14 (see `platform/stack`); accepted.
- **At-least-once delivery** can produce duplicates after a publish/ack race; downstream consumers MUST be idempotent (e.g., `mail-sender` already keys on `X-Event-Id`). Mitigation: documented in `system/kafka-messaging` and `system/runtime-guarantees`; idempotency is a consumer concern.
- **Retry/retention misconfiguration** can bloat `message_outbox` (retries persisting, DEAD accumulating). Mitigation: defaults are conservative (`maxAttempts=10`, `publishedRetention=7d`, `deadRetention=30d`); backlog/retry/dead metrics surface drift.
- **JSONB binding** is hand-written SQL with no Spring Data abstractions; schema drift between the shipped changelog and the DAO SQL is possible. Mitigation: the DAO integration test pins the column set; the shipped SQL and changelog are the single source.
- **`MANDATORY` propagination** will throw if any future `users`/`chats` call site invokes `OutboxEmitter.emit` outside a transaction. Mitigation: intended behavior (spec requires same-transaction write); existing call sites are inside `@Transactional`.
- **Dedicated `outboxTaskScheduler`** adds a thread pool (size 2) per service; acceptable for the expected relay/cleanup load.
- **Liquibase include path stored in `DATABASECHANGELOG`** differs per service, but each service has its own changelog table, so no cross-service collision. Re-running the included changelog on an already-aligned DB is a no-op due to preconditions.
- **Property namespace migration** (`scheduler.message-outbox-*` -> `tx-outbox.*`) is a deployment config change. Mitigation: update both `application.yaml` files in the same change; old keys are removed to fail fast on stale config.

## Migration Plan

1. Build `tx-outbox` core + tests and `tx-outbox-starter` + tests; add both to root `pom.xml`.
2. Update `.agents/rules/architecture/module-boundaries.md`, `openspec/specs/system/architecture/spec.md`, and `openspec/specs/system/runtime-guarantees/spec.md`(via this change's deltas) to permit the new dependency, the core/starter split, and the new delivery guarantee.
3. In `users`: add `tx-outbox-starter` dependency; switch `outboxService.save(...)` -> `OutboxEmitter.emit(type, version, key, bodyPayload)`; declare `tx-outbox.*` properties; drop the old `message_outbox` table and include the shipped changelog (or copy SQL); delete duplicated outbox files (entity/repository/outbox package) and outbox scheduling from `ScheduleConfig`; update the main spec `runtime-guarantees` Current Behavior bullets that no longer apply.
4. In `chats`: same.
5. Run `users` and `chats` migrations against a Testcontainers DB to confirm the new `message_outbox` schema is created and claim/lease/retry SQL works.
6. Run `.\mvnw.cmd -pl tx-outbox,tx-outbox-starter,users,chats -am test`.

Rollback: revert the dependency/import/migration-include changes and restore the per-service outbox packages from git. The new `message_outbox` table is empty after rollback; old outbox state is lost (transient). The delivery guarantee reverts to the previous "mark without ack" behavior until rollback is complete.