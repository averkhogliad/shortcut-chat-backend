# Proposal

## Why

`messages` publishes `MessagePublished` to `ChatLifecycle` by sending directly to `KafkaTemplate` in an `afterCommit` callback. If the process crashes after the aggregate commit and before the send completes, the event is lost; there is no at-least-once guarantee and no retry. All other chat lifecycle producers (`users`, `chats`) already use the transactional outbox, so `messages` is the only producer that can silently drop a lifecycle event.

## What Changes

- `messages` emits `MessagePublished` through the transactional outbox (`tx-outbox-starter`) in the same transaction as the message aggregate change, instead of the direct post-commit `KafkaTemplate.send`.
- `messages/application.yaml` gets `tx-outbox` configuration: a `chat-lifecycle` channel to topic `ChatLifecycle` and a route `MessagePublished` → `chat-lifecycle`.
- `messages` gains an outbox `MessageFactory` for `MessagePublished` (type `MessagePublished`, version `v1`, body `Message`), replacing the current `ProducerRecord` factory + `ChatLifecycleEventPublisher` + `afterCommit` path.
- Serialization for `messages` producer changes to the outbox serializer (JSON bytes produced by `tx-outbox-starter`), consistent with `users` and `chats`; the `spring.json.use.type.headers=false` producer property is no longer used for `MessagePublished`.
- No new REST endpoints, no new event types, no consumer changes, no new projections. `notifications` continues to resolve `MessagePublished` from `X-Event-Name`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/kafka-messaging`: serialization per producer for `messages` changes from direct JSON with type headers disabled to the outbox serializer; topic/event inventory and headers are unchanged in observable behavior.
- `system/runtime-guarantees`: `messages` SHALL publish `MessagePublished` through the transactional outbox with at-least-once delivery, removing the post-commit direct-send gap.

## Impact

- Code: `messages` core output layer (`ChatLifecycleEventPublisher`, `MessageCreatedMessageFactoryImpl`, `EventPublisher` usage in `MessageService`), `messages/application.yaml`, `messages/pom.xml` (add `tx-outbox-starter` dependency).
- Specs: `system/kafka-messaging`, `system/runtime-guarantees` deltas.
- Dependencies: `messages` depends on `tx-outbox-starter` (already present in `chats`/`users`).
- Outbox schema: `messages` needs the `message_outbox` table; migrations come from `tx-outbox-starter` artifacts (same as `users`/`chats`).
- Consumers: no change; `notifications` keeps consuming `ChatLifecycle` and resolving `MessagePublished` by `X-Event-Name`.
- Tests: `messages` unit/integration tests updated to verify outbox emission instead of direct Kafka send; existing `MessageEndpointTest` Kafka expectations preserved.