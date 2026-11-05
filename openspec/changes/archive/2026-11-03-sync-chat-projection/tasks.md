# Tasks

## 1. `messages` — payload DTO and type resolver

- [x] 1.1 Create `ChatCreatedPayload` (`id: UUID`, `name: String`, `owner: UserRef?`, `members: Collection<UserRef>`, `createdAt: Instant`) in `messages/core/model`. Verify: compiles; fields match `ChatCreatedEvent` from `kafka-messaging` spec.
- [x] 1.2 Create `ChatLifecycleTypeResolver` (`JsonTypeResolver`) in `messages/kafka` — resolve `X-Event-Name=ChatCreated` → `ChatCreatedPayload`, else `Map<String, Any?>`. Pattern: `UserLifecycleTypeResolver`. Verify: unit test `ChatLifecycleTypeResolverTest` for `ChatCreated` and unknown.

## 2. `messages` — Kafka config and consumer

- [x] 2.1 In `messages/config/KafkaConfig` add bean `KAFKA_LISTENER_CHAT_LIFECYCLE` (`ConcurrentKafkaListenerContainerFactory`) with `JsonDeserializer` + `ChatLifecycleTypeResolver`, type headers disabled. Pattern: existing `KAFKA_LISTENER_USER_LIFECYCLE`. Verify: context starts, bean present.
- [x] 2.2 Create `ChatLifecycleEventsHandler` (`@KafkaListener(topics=["ChatLifecycle"], containerFactory=KAFKA_LISTENER_CHAT_LIFECYCLE)`) with `@KafkaHandler` for `ChatCreatedPayload` (extract `X-Published-At`, delegate to `ChatProjectionService`) and default handler (warn). Pattern: `UserLifecycleEventsHandler`. Verify: unit test with mock service — `ChatCreated` calls service; unknown → warning, no service call.

## 3. `messages` — projection service

- [x] 3.1 Remove `@LastModifiedDate` from `ChatEntity.lastSyncAt` in `messages`. Verify: compiles; no other code depends on this annotation.
- [x] 3.2 Create `ChatProjectionService` (`@Transactional` method `applyCreated(id, eventTime, payload)`): load by `id` via `ChatRepository.findById`; if found and `eventTime <= lastSyncAt` → ignore; if found and newer → update `name` and `lastSyncAt`, save; if not found → create `ChatEntity` with `id`, `name`, `lastSyncAt=eventTime`, `deleted=false`, `members=emptySet()`, insert via `jdbcAggregateTemplate.insert`. Pattern: `UserProjectionService`. Verify: unit tests with mock `ChatRepository` and `JdbcAggregateTemplate` — insert, upsert, ignore-stale.
- [x] 3.3 `ChatRepository` already declares `findById` and `save`. Verify: no additional methods needed.

## 4. Integration tests

- [x] 4.1 Create `ChatLifecycleConsumerTest` (integration, `@SpringBootTest` + Testcontainers + Kafka): publish `ChatCreated` to `ChatLifecycle` → assert `chats` row inserted; duplicate `ChatCreated` with same event time → no change; stale `ChatCreated` with older event time → ignored. Pattern: `UserLifecycleConsumerTest`. Verify: test green.
- [x] 4.2 Extend `MessageEndpointTest` or add test: sync chat via `ChatCreated` event, then `POST /messages` with that `chatId` succeeds (no FK violation). Verify: test green.

## 5. Specs and verification

- [x] 5.1 Verify `openspec validate --changes sync-chat-projection --strict` passes.
- [x] 5.2 Sync deltas to `openspec/specs/system/architecture/spec.md`, `openspec/specs/system/kafka-messaging/spec.md`, `openspec/specs/system/runtime-guarantees/spec.md`. Verify `openspec validate --specs` passes.
- [x] 5.3 Run `.\mvnw.cmd -pl messages -am test`. Verify: build and tests green (subject to Docker/Testcontainers availability).

## 6. Archive

- [x] 6.1 Archive the change after sync and verification.