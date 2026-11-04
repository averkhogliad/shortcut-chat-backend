# Proposal

## Why

Several internal implementation issues identified during the domain-data-flow audit do not affect externally observable behavior but reduce code quality, ordering guarantees, and maintainability.

## What Changes

- Upgrade every dependency to the latest stable version: Spring Boot 4.1.2 (Spring Framework 7, Spring Kafka 4, Jackson 3), Kotlin 2.4.21, Kotest 6.2.6, MockK 1.14.11, Testcontainers 2.0.5, ShedLock 7.10.1, uuid-generator 5.2.0, hypersistence-tsid 2.1.4, loki-logback-appender 2.0.3, Jakarta Mail API 2.1.5, Maven 3.9.16 / Maven Wrapper 3.3.4. Remove the unused SpringMockK dependency (no Boot 4 release). Migrate Jackson 2 imports to Jackson 3, Kotest/Kafka/Testcontainers APIs to their new majors. After implementation, actualize the Domain Profile and affected Specs to the new facts.
- Set `key = user.id.toString()` in `UserCreatedMessageFactory`, `UserUpdatedMessageFactory`, `UserDeletedMessageFactory` so all events for one user go to the same Kafka partition.
- Set `X-Event-Id = message.id.toString()` in `MessageCreatedMessageFactory` instead of random UUID, enabling deduplication by message id.
- Move Kafka header constants (`X-Event-Id`, `X-Event-Name`, etc.) from `messages/.../ChatCreatedMessageFactory.kt` to `common`; `tx-outbox-starter` `OutboxHeaderNames` delegates to the `common` constants so there is a single source of values.
- Evaluate whether `ChatLifecycleTypeResolver` in `notifications` and `UserLifecycleTypeResolver` in `chats`/`messages` can share a base class or utility in `common`; extract if the logic is genuinely shared, otherwise record the decision.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/tx-outbox`: the `X-Event-*` header-name constants live in `common`; the starter header provider delegates to them (interface stays in the starter).
- `platform/stack`: pin the upgraded dependency versions (Spring Boot 4.1.2, Kotlin 2.4.21, Kotest 6.2.6, MockK 1.14.11, Testcontainers 2.0.5, ShedLock 7.10.1, java-uuid-generator 5.2.0, Jakarta Mail API 2.1.5, loki-logback-appender 2.0.3, Maven 3.9.16 / Maven Wrapper 3.3.4) and the test infrastructure (Kotest 6 `SpringExtension`, Testcontainers 2 artifacts/packages, Spring Boot 4 test modules). SpringMockK is removed: it is unused and has no Spring Boot 4 release. loki 2.1.0 is not taken because it requires Logback 1.6, while Boot 4.1.2 manages 1.5.38; Spring Boot 4.2.0-RC1, Kotlin 2.5.0-Beta2 and Maven 4.0.0-rc are previews and are not taken.

## Impact

- **Code**: POMs, wrapper and all `src/main` (Jackson 3, Spring Kafka 4, Spring Framework 7 migration), `common-test` and all `src/test` (Kotest 6, Testcontainers 2, Boot 4 test modules), `users` message factories (3 files), `messages` event factory (1 file), `common` (new constants file), `notifications` TypeResolver (constants), `tx-outbox-starter` `OutboxHeaderNames` (delegates to `common`).
- **Docs**: `.agents/context/backend.md` and any Spec contradicting the implemented facts, actualized after implementation.
- **Specs**: `platform/stack` delta with the resolved dependency versions.
- **Kafka**: users events gain partition key (ordering improvement, no contract change). Messages `X-Event-Id` becomes deterministic (enables dedup, no contract change).
- **Tests**: update factory unit tests to verify key and event id.