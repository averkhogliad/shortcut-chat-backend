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

### Requirement: User projection idempotency and ordering

`chats` and `messages` SHALL apply `UserLifecycle` events idempotently per user id. Each local projection row SHALL track the timestamp of the last applied event (`last_sync_at`). A consumer SHALL ignore an event whose event time is not newer than the stored `last_sync_at` for that user id, and SHALL update `last_sync_at` to the applied event time on success.

#### Scenario: Duplicate delivery after outbox retry

- **WHEN** a consumer receives the same `UserCreated` or `UserUpdated` event again
- **THEN** it SHALL detect the duplicate by user id and event time and SHALL NOT re-apply the change.

#### Scenario: Out-of-order event

- **WHEN** a consumer receives a `UserUpdated` whose event time is older than the stored `last_sync_at`
- **THEN** it SHALL ignore the event and keep the newer projection state.

#### Scenario: First event for a user

- **WHEN** a consumer receives `UserCreated` for a user id absent from the local projection
- **THEN** it SHALL insert the projection row and set `last_sync_at` to the event time.

### Requirement: User lifecycle events use the transactional outbox

`users` SHALL publish `UserUpdated` and `UserDeleted` through the transactional outbox in the same transaction as the aggregate change, with at-least-once delivery, consistent with `UserCreated`.

#### Scenario: User updated transactionally

- **WHEN** `users` updates a user aggregate
- **THEN** the aggregate change and the `UserUpdated` outbox record SHALL be committed in one transaction.

#### Scenario: User deleted transactionally

- **WHEN** `users` deletes a user aggregate
- **THEN** the aggregate change and the `UserDeleted` outbox record SHALL be committed in one transaction.

## Current Behavior

These are observed current behaviors, not requirements.

- `messages` publishes `MessagePublished` after commit without an outbox. Delivery is not guaranteed.
- `mail-sender` stores `FAILED` and does not rethrow the exception. Retry and DLQ are not implemented.
- `notifications` exposes a live-only SSE stream without replay.
- Synchronization of `users` read models in `chats` and `messages` is not implemented.
