# Runtime Guarantees Specification

## Purpose

Define transaction boundaries, outbox processing, and idempotency behavior, and document current runtime behaviors that are not requirements.

## Requirements

### Requirement: Transaction boundaries

Services SHALL apply `@Transactional` at the service layer. Post-commit actions SHALL run through the transaction callback `transaction { afterCommit { ... } }`.

#### Scenario: Post-commit action

- **WHEN** a service needs to run an action after a successful commit
- **THEN** the action SHALL be registered through `afterCommit`.

### Requirement: Outbox write and processing

`users` and `chats` SHALL write the outbox record in the same transaction as the aggregate change. The outbox handler SHALL publish pending records and mark them published.

#### Scenario: Pending outbox record

- **WHEN** the outbox handler processes a pending record
- **THEN** it SHALL publish the record to its target topic and mark it published.

### Requirement: Mail idempotency state

`mail-sender` SHALL store delivery state in Redis under the event id and SHALL use a TTL of 30 days.

#### Scenario: Completed delivery

- **WHEN** a mail command with the same `X-Event-Id` is received again
- **THEN** the stored `COMPLETED` state SHALL prevent a second send.

## Current Behavior

These are observed current behaviors, not requirements. Decisions about guarantees, retries, and replay are pending.

- `messages` publishes `MessagePublished` after commit without an outbox. Delivery is not guaranteed.
- The outbox handler marks a record as published without waiting for a Kafka acknowledgement. This is a known technical risk.
- `mail-sender` stores `FAILED` and does not rethrow the exception. Retry and DLQ are not implemented.
- `notifications` exposes a live-only SSE stream without replay.
- Synchronization of `users` read models in `chats` and `messages` is not implemented.
