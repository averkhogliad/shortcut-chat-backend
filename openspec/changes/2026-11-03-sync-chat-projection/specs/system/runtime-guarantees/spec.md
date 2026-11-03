# Spec Delta

## ADDED Requirements

### Requirement: Chat projection idempotency and ordering

`messages` SHALL apply `ChatLifecycle` events idempotently per chat id. The local projection row SHALL track the timestamp of the last applied event (`last_sync_at`). A consumer SHALL ignore an event whose event time is not newer than the stored `last_sync_at` for that chat id, and SHALL update `last_sync_at` to the applied event time on success.

#### Scenario: Duplicate delivery after outbox retry

- **WHEN** a consumer receives the same `ChatCreated` event again
- **THEN** it SHALL detect the duplicate by chat id and event time and SHALL NOT re-apply the change.

#### Scenario: First event for a chat

- **WHEN** a consumer receives `ChatCreated` for a chat id absent from the local projection
- **THEN** it SHALL insert the projection row and set `last_sync_at` to the event time.