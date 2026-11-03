# Proposal

## Why

Several internal implementation issues identified during the domain-data-flow audit do not affect externally observable behavior but reduce code quality, ordering guarantees, and maintainability.

## What Changes

- Upgrade all libraries and dependencies to their latest compatible versions: Spring Boot (3.4.0 → current 3.5.x line), Kotlin, Kotest, MockK, Testcontainers, shedlock, uuid-generator and the rest of the parent POM versions.
- Set `key = user.id.toString()` in `UserCreatedMessageFactory`, `UserUpdatedMessageFactory`, `UserDeletedMessageFactory` so all events for one user go to the same Kafka partition.
- Set `X-Event-Id = message.id.toString()` in `MessageCreatedMessageFactory` instead of random UUID, enabling deduplication by message id.
- Move Kafka header constants (`X-Event-Id`, `X-Event-Name`, etc.) from `messages/.../ChatCreatedMessageFactory.kt` to `common`.
- Move `ChatLifecycleTypeResolver` from `notifications` to a shared module (or `common`) to avoid duplication with `messages` and `chats` resolvers.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none — no spec-level behavior changes; `skip_specs: true`)

## Impact

- **Code**: `users` message factories (3 files), `messages` event factory (1 file), `common` (new constants file), `notifications` TypeResolver (move).
- **Specs**: none.
- **Kafka**: users events gain partition key (ordering improvement, no contract change). Messages `X-Event-Id` becomes deterministic (enables dedup, no contract change).
- **Tests**: update factory unit tests to verify key and event id.