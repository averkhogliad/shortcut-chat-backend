# Spec Delta

## ADDED Requirements

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