# Proposal

## Why

`messages` owns a local `chats` table (`id`, `name`, `last_sync_at`, `is_deleted`) with a foreign key `messages.chat_id → chats.id`. `ChatEntity` and `ChatRepository` are declared but never populated: no consumer reads `ChatLifecycle`. On an empty database, `POST /messages` fails on FK violation because the chat row does not exist locally.

User projection sync was implemented in the `sync-user-projection` change. Chat projection sync is the analogous gap: `chats` publishes `ChatCreated` to `ChatLifecycle`, but `messages` does not consume it.

## What Changes

- `system/kafka-messaging`: add `messages` as a consumer of `ChatCreated` on `ChatLifecycle`; add chat lifecycle consumer dispatch requirement; extend serialization requirement to cover `messages` resolving `ChatLifecycle` payload by `X-Event-Name`.
- `system/architecture`: add requirement "Chat projection synchronization in messages" — `messages` SHALL maintain a local chat projection by consuming `ChatCreated`; the projection SHALL satisfy FK constraints on message creation.
- `system/runtime-guarantees`: add requirement "Chat projection idempotency and ordering" — `messages` SHALL apply `ChatCreated` idempotently per chat id using `last_sync_at`, same pattern as user projection.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/kafka-messaging`: `ChatCreated` consumer list gains `messages`; new dispatch and serialization requirements.
- `system/architecture`: new chat projection sync requirement.
- `system/runtime-guarantees`: new chat projection idempotency requirement.

## Impact

- Specs: `system/kafka-messaging`, `system/architecture`, `system/runtime-guarantees`.
- Code: `messages` module — new `ChatLifecycleTypeResolver`, `ChatLifecycleEventsHandler`, `ChatProjectionService`; `KafkaConfig` gains `ChatLifecycle` listener; `application.yaml` already has consumer group and `auto-offset-reset: earliest`.
- Dependencies: none new (`messages` already has Spring Kafka).
- Consumers: `notifications` unaffected (separate consumer group).
- Tests: unit tests for handler and projection service; integration test for `ChatLifecycle` → `chats` projection row; `POST /messages` succeeds after projection sync.