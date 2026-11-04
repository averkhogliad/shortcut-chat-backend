# Tasks

## 1. Upgrade libraries and dependencies to the latest stable versions

- [x] 1.1 Update every version in the parent POM and module POMs to the latest stable: Spring Boot 4.1.2, Kotlin 2.4.21, MockK 1.14.11, Testcontainers 2.0.5 (TC 2 artifact coordinates), ShedLock 7.10.1, `java-uuid-generator` 5.2.0, `hypersistence-tsid` 2.1.4, Jakarta Mail API 2.1.5, `loki-logback-appender` 2.0.3, Kotest 6.2.6 with `io.kotest:kotest-extensions-spring` / `kotest-extensions-testcontainers` 6.2.6; remove the unused SpringMockK dependency. Verify: `.\mvnw.cmd -q compile` succeeds for `src/main`; no version property keeps an older line without a documented technical exception.
- [x] 1.2 Migrate Jackson 2 → Jackson 3 in main sources: `com.fasterxml.jackson.databind|core` → `tools.jackson.databind|core` (annotations stay `com.fasterxml.jackson.annotation`); update `ObjectMapper`/`JavaType`/`TypeFactory`/`TypeReference` usages and Spring Jackson configuration. Verify: `.\mvnw.cmd -q compile` succeeds; no Jackson 2 databind/core imports remain.
- [x] 1.3 Migrate Kafka serialization to Spring Kafka 4 Jackson 3 integration: use `JacksonJsonDeserializer` / `JacksonJsonTypeResolver` in `chats`, `messages`, `notifications`; keep payload type resolution by `X-Event-Name` and disabled type headers. Verify: `.\mvnw.cmd -q compile` succeeds.
- [x] 1.4 Migrate test infrastructure: Kotest 6 (`SpringAwareKotestConfig`, `SqlTestCaseExtension`, spec/property/matcher API), Testcontainers 2 coordinates and packages (`testcontainers-postgresql`, `testcontainers-kafka`, `testcontainers-junit-jupiter`; `org.testcontainers.postgresql`, `org.testcontainers.kafka`), Boot 4 test-module packages (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest`, JDBC test auto-configuration), Spring Boot 4 starter/test module moves. Verify: all tests compile; `.\mvnw.cmd test` green for all modules (Docker required).
- [x] 1.5 Review Spring Boot 4 / Spring Framework 7 migration notes for breaking changes affecting used features (Kafka, Security, Data JDBC, WebFlux, MVC, Actuator, Micrometer). Apply required code/config adjustments. Verify: full `.\mvnw.cmd test` green.
- [x] 1.6 Check for deprecated APIs used in the codebase and fix compile warnings introduced by the upgrades; do not use deprecated Testcontainers bridges. Verify: no new deprecation warnings on compile.
- [x] 1.7 Update the `platform/stack` spec delta with the final resolved versions (`openspec/changes/tech-debt-cleanup/specs/platform/stack/spec.md`). Verify: `openspec validate tech-debt-cleanup --strict` passes.
- [x] 1.8 Update the Maven wrapper to wrapper 3.3.4 / Maven 3.9.16 (`maven-wrapper.properties`, wrapper scripts). Verify: `.\mvnw.cmd -v` reports the new versions and the build still runs.

## 2. Kafka key for users events

- [x] 2.1 Set `key = user.id.toString()` in `UserCreatedMessageFactory`, `UserUpdatedMessageFactory`, `UserDeletedMessageFactory`. Verify: unit tests assert key equals user id.
- [x] 2.2 Set `key = userDeleted.id.toString()` in `UserDeletedMessageFactory`. Verify: unit test asserts key.
- [x] 2.3 Verify the key reaches the outbox record. Verify: `UserEndpointTest` asserts `message_outbox.key` equals the user id for `UserCreated`, `UserUpdated`, `UserDeleted`; the `SendCreatedUserNotification` (`MailerCommands`) record keeps `key = null`.

## 3. Deterministic X-Event-Id for messages

- [x] 3.1 Replace `UUID.randomUUID()` with `event.id.toString()` in `MessageCreatedMessageFactory.create()`. Verify: unit test asserts `X-Event-Id` equals message id; two calls for same message produce same event id; the chat id record key is unchanged.

## 4. Shared Kafka constants

- [x] 4.1 Create `common/.../kafka/KafkaHeaders.kt` with `X-Event-Id`, `X-Event-Name`, `X-Event-Version`, `X-Published-At`, `X-Published-By` constants. Verify: compiles.
- [x] 4.2 Replace local constants in `messages/.../ChatCreatedMessageFactory.kt` with import from `common`. Verify: compiles, existing tests pass.
- [x] 4.3 Replace local constants in `notifications/.../ChatLifecycleTypeResolver.kt` with import from `common`; update `ChatLifecycleEventsHandler` and test imports. Verify: compiles, existing tests pass.
- [x] 4.4 Make `tx-outbox-starter` `OutboxHeaderNames` delegate to the `common` constants; keep its public API and values unchanged. Verify: `KafkaTemplateOutboxPublisherTest` and outbox tests pass.
- [x] 4.5 Declare the direct `common` dependency in `tx-outbox-starter/pom.xml` (`system/architecture` spec requires `tx-outbox-starter` to depend on `common`). Verify: `.\mvnw.cmd -pl tx-outbox-starter -am test` green.

## 5. Shared TypeResolver

- [x] 5.1 Evaluate whether `ChatLifecycleTypeResolver` in `notifications` and `UserLifecycleTypeResolver` in `chats`/`messages` can share a base class or utility in `common`. If yes, extract common logic. If the resolvers are too different (different event name mappings), skip and record. Verify: decision documented; if code changed — affected module tests green and event resolution behavior unchanged.

## 6. Actualize context and specs after implementation

- [x] 6.1 Update `.agents/context/backend.md` to the implemented facts: versions (Boot 4.1.2, Kotlin 2.4.21, Jackson 3, Kotest 6, Testcontainers 2, Maven wrapper 3.3.4 / Maven 3.9.16), SpringMockK removal, `hypersistence-tsid` version, Boot 4 test-module packages, and any changed serialization mechanism descriptions. Verify: every fact matches the POM/code.
- [x] 6.2 Check the Specs for contradictions with the implemented facts (`platform/stack` after sync; `system/kafka-messaging` serialization wording; other version references). Update only documented contradictions; do not introduce new requirements. Verify: `openspec validate --strict` passes.

## Deferred follow-up

Kafka header-name constants remain duplicated as private copies in `chats`/`messages` `UserLifecycleTypeResolver`/`UserLifecycleEventsHandler` and in test sources. Inventory recorded in `.execution/tech-debt-cleanup/plan.md`. Whether to DRY them is deferred to a separate decision.

## Workflow follow-up

- Archive the change after implementation and verification.

