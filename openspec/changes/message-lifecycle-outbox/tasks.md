# Tasks

## 1. `messages` — outbox dependency and configuration

- [ ] 1.1 Add `tx-outbox-starter` dependency to `messages/pom.xml` (same version/groupId as `chats`). Verify `mvnw -pl messages compile` succeeds and the starter auto-configuration is on the classpath.
- [ ] 1.2 Add `tx-outbox` config to `messages/src/main/resources/application.yaml`: `enabled: true`, channel `chat-lifecycle` → topic `ChatLifecycle`, route `MessagePublished` → `chat-lifecycle`, plus relay/lease/retry/cleanup settings matching `chats`. Verify `MessageApp` context starts without `TxOutboxProperties` validation errors.
- [ ] 1.3 Add the `message_outbox` Liquibase changelog to `messages` migrations (include the `tx-outbox-starter` changelog the same way `chats`/`users` do). Verify `mvnw -pl messages test` brings up the schema and the `message_outbox` table exists.

## 2. `messages` — outbox emission

- [ ] 2.1 Create `MessagePublishedMessageFactoryImpl : MessageFactory<Message, Message>` returning `OutboxMessage<Message>` (type `MessagePublished`, version `v1`, key `message.chat.id.toString()`, body `message`). Verify a unit test `MessagePublishedMessageFactoryImplTest` asserts type, version, key, and body equal the input `Message`.
- [ ] 2.2 Switch `MessageService.create` to emit via `OutboxEmitter.emit(outboxMessage.type, outboxMessage.version, outboxMessage.key, outboxMessage.body)` inside the existing `@Transactional` method; remove the `transaction { afterCommit { eventPublisher.publish(it) } }` block. Verify `MessageServiceTest` (or a new unit test) captures the `OutboxEmitter.emit` call and asserts type=`MessagePublished`, version=`v1`, key=`chat.id`, body=`message`.
- [ ] 2.3 Remove `ChatLifecycleEventPublisher`, `EventPublisher`, and the `MessageCreatedMessageFactoryImpl`/`ProducerRecordFactory` direct-send path from `messages` production code. Verify `mvnw -pl messages compile` succeeds with no references to `KafkaTemplate`/`ProducerRecordFactory` in the publish path.

## 3. `messages` — producer serialization cleanup

- [ ] 3.1 Remove the `spring.kafka.producer` `JsonSerializer` / `spring.json.use.type.headers=false` configuration for `MessagePublished` from `messages/application.yaml` (outbox serializer replaces it). Verify the `messages` Kafka consumer config for `UserLifecycle` remains intact and `MessageApp` starts.
- [ ] 3.2 Update `MessageEndpointTest` (and any integration test asserting a `MessagePublished` Kafka record) to expect the event delivered via the outbox relay on topic `ChatLifecycle` with headers `X-Event-Name=MessagePublished` and the standard set, payload `Message`. Verify `mvnw -pl messages test` is green.

## 4. Integration verification and specs reconciliation

- [ ] 4.1 Run `mvnw -pl messages,chats,notifications -am test` and verify BUILD SUCCESS; `notifications` still consumes `MessagePublished` from `ChatLifecycle` and SSE behavior is unchanged.
- [ ] 4.2 Run `openspec validate --changes message-lifecycle-outbox --strict` and verify it passes; reconcile delta specs vs code (serialization per producer, outbox transactionality) — no discrepancy remains.
- [ ] 4.3 Negative scope check: `users`, `chats`, `notifications`, `mail-sender`, `common`, `tx-outbox`, `tx-outbox-starter`, and `openspec/specs/` are unchanged; `messages` REST endpoints and `Message` payload are unchanged. Verify via `git diff --stat` against the base.

## Workflow follow-up

- Archive the change after review (`openspec archive message-lifecycle-outbox`).
- After archive, update `system/runtime-guarantees` "Current Behavior" note about `messages` direct send if it remains stale.