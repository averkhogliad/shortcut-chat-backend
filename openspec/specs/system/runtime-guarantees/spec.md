# Runtime Guarantees Specification

## Purpose

Define transaction boundaries, outbox processing, and idempotency behavior, and document current runtime behaviors that are not requirements.

## Requirements

### Requirement: Transaction boundaries

Services SHALL apply `@Transactional` at the service layer. Post-commit actions SHALL run through the transaction callback `transaction { afterCommit { ... } }`.

#### Scenario: Post-commit action

- **WHEN** a service needs to run an action after a successful commit
- **THEN** the action SHALL be registered through `afterCommit`.

### Requirement: Outbox write is transactional

`users` and `chats` SHALL write the outbox record in the same transaction as the aggregate change.

#### Scenario: Record is written with the aggregate change

- **WHEN** `users` or `chats` changes an aggregate that produces an event
- **THEN** the aggregate change and the outbox record SHALL be committed in one transaction.

### Requirement: Outbox relay claims and publishes records

The outbox relay SHALL claim pending records, publish them through an `OutboxPublisher`, and transition them through `NEW`, `CLAIMED`, `RETRY_WAIT`, `PUBLISHED`, and `DEAD` states.

#### Scenario: Pending outbox record

- **WHEN** the relay processes a pending record
- **THEN** it SHALL publish the record to its target topic and transition it to `PUBLISHED`.

#### Scenario: Pending record is claimed and published

- **WHEN** the relay claims a `NEW` record and the publisher succeeds
- **THEN** the record SHALL transition to `PUBLISHED`.

### Requirement: Outbox retry and dead-letter

Records that exceed the configurable retry limit SHALL transition to `DEAD`. Retry SHALL use exponential backoff.

#### Scenario: Publish failure schedules retry

- **WHEN** publishing a claimed record fails and the attempt count is below the limit
- **THEN** the record SHALL transition to `RETRY_WAIT` with exponential backoff up to `maxAttempts`, after which it SHALL transition to `DEAD`.

### Requirement: Outbox leases guard claimed records

Claimed records SHALL be leased to a relay owner until publication or lease expiry, and expired leases SHALL be recovered.

#### Scenario: Expired lease is recovered

- **WHEN** a claimed record's lease has expired
- **THEN** the relay SHALL recover it for re-publish.

### Requirement: Terminal outbox records are cleaned by retention

Terminal records (`PUBLISHED`, `DEAD`) SHALL be cleaned by retention.

#### Scenario: Terminal records are cleaned

- **WHEN** cleanup runs and a `PUBLISHED` or `DEAD` record is older than its retention
- **THEN** the record SHALL be deleted.

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

### Requirement: Message published directly after commit

`messages` SHALL publish `MessagePublished` to `ChatLifecycle` directly from a post-commit callback without the transactional outbox. Delivery is at-most-once: if the process fails after the message aggregate commit and before the Kafka send completes, the event is lost. This trade-off is deliberate to minimize publish latency for interactive message creation. `messages` SHALL NOT write an outbox record or depend on `tx-outbox-starter` for `MessagePublished`.

#### Scenario: Message published after commit

- **WHEN** `messages` creates a message aggregate and the transaction commits
- **THEN** `messages` SHALL publish `MessagePublished` to `ChatLifecycle` in a post-commit callback.

#### Scenario: Crash after commit before send

- **WHEN** the process crashes after the message aggregate commit but before the Kafka send completes
- **THEN** the `MessagePublished` event SHALL NOT be delivered, and the loss is accepted as the at-most-once trade-off for lower latency.

#### Scenario: No outbox dependency

- **WHEN** `messages` publishes `MessagePublished`
- **THEN** it SHALL NOT write an outbox record and SHALL NOT depend on `tx-outbox-starter`.

## Current Behavior

These are observed current behaviors, not requirements.

- `mail-sender` stores `FAILED` and does not rethrow the exception. Retry and DLQ are not implemented.
- `notifications` exposes a live-only SSE stream without replay.
- `chats` and `messages` maintain a local `users` read model from `UserLifecycle` events; idempotency and ordering are defined in the requirements above.
