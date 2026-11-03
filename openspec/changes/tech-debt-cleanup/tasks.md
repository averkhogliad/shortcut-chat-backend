# Tasks

## 1. Upgrade libraries and dependencies

- [ ] 1.1 Update Spring Boot version in parent POM to the latest 3.5.x release. Verify: `.\mvnw.cmd -q compile` succeeds for all modules.
- [ ] 1.2 Update Kotlin, Kotest, Kotest extensions, MockK, SpringMockK, Testcontainers, shedlock, uuid-generator, loki-logback-appender versions in parent POM to latest compatible releases. Verify: `.\mvnw.cmd -q compile` succeeds; run `.\mvnw.cmd test` for all modules green.
- [ ] 1.3 Review Spring Boot 3.5 migration notes for breaking changes affecting used features (Kafka, Security, Data JDBC, WebFlux). Apply required code/config adjustments. Verify: full `.\mvnw.cmd test` green.
- [ ] 1.4 Check for deprecated APIs used in the codebase and fix compile warnings introduced by the upgrades. Verify: no new deprecation warnings on compile.

## 2. Kafka key for users events

- [ ] 2.1 Set `key = user.id.toString()` in `UserCreatedMessageFactory`, `UserUpdatedMessageFactory`, `UserDeletedMessageFactory`. Verify: unit tests assert key equals user id.
- [ ] 2.2 Set `key = userDeleted.id.toString()` in `UserDeletedMessageFactory`. Verify: unit test asserts key.

## 3. Deterministic X-Event-Id for messages

- [ ] 3.1 Replace `UUID.randomUUID()` with `event.id.toString()` in `MessageCreatedMessageFactory.create()`. Verify: unit test asserts `X-Event-Id` equals message id; two calls for same message produce same event id.

## 4. Shared Kafka constants

- [ ] 4.1 Create `common/.../kafka/KafkaHeaders.kt` with `X-Event-Id`, `X-Event-Name`, `X-Event-Version`, `X-Published-At`, `X-Published-By` constants. Verify: compiles.
- [ ] 4.2 Replace local constants in `messages/.../ChatCreatedMessageFactory.kt` with import from `common`. Verify: compiles, existing tests pass.
- [ ] 4.3 Replace local constants in `notifications/.../ChatLifecycleTypeResolver.kt` with import from `common`. Verify: compiles, existing tests pass.

## 5. Shared TypeResolver

- [ ] 5.1 Evaluate whether `ChatLifecycleTypeResolver` in `notifications` and `UserLifecycleTypeResolver` in `chats`/`messages` can share a base class or utility in `common`. If yes, extract common logic. If the resolvers are too different (different event name mappings), skip and record. Verify: decision documented.

## Workflow follow-up

- Archive the change after implementation and verification.