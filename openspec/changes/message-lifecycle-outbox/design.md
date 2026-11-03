# Design

## Context

`messages` is the only chat lifecycle producer not using the transactional outbox. `MessageService.create` is `@Transactional` and, after saving the aggregate, registers an `afterCommit { eventPublisher.publish(message) }` callback. `ChatLifecycleEventPublisher` builds a `ProducerRecord` via `MessageCreatedMessageFactoryImpl` (a `ProducerRecordFactory`) and calls `KafkaTemplate.send`. If the JVM dies between commit and the send completing, the message row is committed but `MessagePublished` is never delivered; there is no retry and no at-least-once guarantee.

`users` and `chats` already publish through `tx-outbox-starter`: a `MessageFactory` produces an `OutboxMessage`, `OutboxEmitter.emit(type, version, key, body)` writes an outbox row in the current transaction, and the starter's relay publishes it with claim/lease, retry, and cleanup. `messages` already depends on `common` and Spring Kafka, but not on `tx-outbox-starter`.

The outbox infrastructure is domain-neutral: routes map an outbox type to a channel/topic, and the starter's default Kafka publisher serializes the body to JSON bytes and attaches the standard header set (`X-Event-Id`, `X-Event-Name`, `X-Event-Version`, `X-Published-At`, `X-Published-By`). `notifications` already resolves `MessagePublished` by `X-Event-Name`, so it does not rely on JSON type headers.

## Goals / Non-Goals

**Goals:**
- `MessagePublished` is emitted with the same at-least-once delivery guarantee as `ChatCreated` and `UserLifecycle` events.
- The outbox row is written in the same transaction as the `messages` aggregate change.
- No change to the `ChatLifecycle` topic, event name, payload (`Message`), or consumer behavior.

**Non-Goals:**
- No new REST endpoints, no new event types, no member/chat mutation events.
- No consumer changes, no new projections, no `messages` chat projection.
- No refactor of the `ProducerRecordFactory`/`EventPublisher` abstraction beyond removing the `messages` direct-send path; the TODO about moving header constants to a common lib is out of scope.
- No change to `notifications` SSE semantics.

## Decisions

### 1. Reuse `tx-outbox-starter` exactly as `chats` does

Add `tx-outbox-starter` as a `messages` dependency. Configure `tx-outbox` in `messages/application.yaml` with a `chat-lifecycle` channel (topic `ChatLifecycle`) and a route `MessagePublished` → `chat-lifecycle`. Emit via `OutboxEmitter` inside `MessageService.create`, replacing the `afterCommit` direct send.

**Why:** Identical pattern to `chats`/`users`; no new outbox variant. The starter owns serialization, headers, scheduling, retry, and cleanup. `messages` only provides the route and the `MessageFactory`.

**Alternative:** Keep direct send and add a custom retry/dead-letter wrapper around `KafkaTemplate`. Rejected: duplicates outbox semantics, diverges from the established pattern, and does not give transactional coupling.

### 2. Replace `ProducerRecordFactory` with an outbox `MessageFactory`

Introduce `MessagePublishedMessageFactoryImpl : MessageFactory<Message, Message>` returning `OutboxMessage<Message>` (type `MessagePublished`, version `v1`, key `message.chat.id.toString()`, body `message`), mirroring `chats/ChatCreatedMessageFactoryImpl`. Remove `ChatLifecycleEventPublisher`, `EventPublisher`, and the `ProducerRecordFactory` usage from the `messages` production path. `MessageService` no longer calls `transaction { afterCommit { ... } }`; it emits the outbox record directly in the existing `@Transactional` method.

**Why:** The outbox `MessageFactory` is the unit the starter serializes; keeping the body as the `Message` model preserves the payload contract. The key stays `chat.id` for partitioning consistency with the existing `ProducerRecord` key.

**Alternative:** Keep `ProducerRecordFactory` and adapt the outbox to accept producer records. Rejected: the outbox API is `OutboxMessage`; mixing record factories adds a layer with no benefit.

### 3. Drop the `spring.json.use.type.headers=false` producer property for `messages`

With the outbox serializer, `messages` no longer configures its own `KafkaTemplate` value serializer for `MessagePublished`. The starter's default publisher produces JSON bytes without Spring type headers (consistent with `users`/`chats`). The `messages` consumer config for `UserLifecycle` is unaffected.

**Why:** Aligns `messages` with the other producers and with the spec delta. `notifications` already resolves type from `X-Event-Name`, so dropping type headers is transparent to consumers.

**Alternative:** Force type headers off in outbox publisher config. Rejected: the starter's default already matches; per-producer override is unnecessary.

### 4. Outbox schema in `messages` database

`messages` uses PostgreSQL and Liquibase. The `tx-outbox-starter` ships the `message_outbox` table changelog (same artifact `chats`/`users` include). Add the starter's changelog to `messages` Liquibase master, or rely on the starter's auto-included migrations as `chats` does.

**Why:** The outbox row must live in the same database/transaction as the message aggregate. `messages` already has PostgreSQL + Liquibase, so no new infrastructure.

**Alternative:** A separate outbox schema/database. Rejected: breaks same-transaction guarantee.

## Risks / Trade-offs

- [Duplicate `MessagePublished` after relay retry] → `notifications` SSE is live-only with no replay, and consumers are expected to be idempotent; duplicates are delivered at-least-once. No new idempotency state is added in `messages` (out of scope). Existing consumer behavior unchanged.
- [Outbox relay latency vs immediate direct send] → Events are published by the scheduler instead of immediately after commit. Accepted: `chats`/`users` already operate this way; relay `fixed-delay` is short. If lower latency is needed later, tune `tx-outbox.relay` schedule (out of scope).
- [Behavioral spec change for `messages` serialization] → The kafka-messaging delta updates the serialization requirement; consumer contract is preserved by `X-Event-Name` resolution.
- [Test churn] → `MessageEndpointTest` and any unit tests asserting `KafkaTemplate.send` / `afterCommit` must switch to asserting `OutboxEmitter.emit` with captured `OutboxMessage`. Covered in tasks.

## Migration Plan

1. Add `tx-outbox-starter` to `messages/pom.xml`.
2. Add `tx-outbox` config and `message_outbox` Liquibase changelog to `messages`.
3. Implement `MessagePublishedMessageFactoryImpl`; switch `MessageService.create` to `OutboxEmitter.emit`.
4. Remove `ChatLifecycleEventPublisher`/`EventPublisher`/`ProducerRecordFactory` and the `afterCommit` path.
5. Update tests to verify outbox emission; run `mvnw -pl messages test`.
6. Rollback: revert the commit; the previous direct-send path is restored. No data migration is required because `message_outbox` is a new table; dropping it reverts cleanly.

## Open Questions

(none)