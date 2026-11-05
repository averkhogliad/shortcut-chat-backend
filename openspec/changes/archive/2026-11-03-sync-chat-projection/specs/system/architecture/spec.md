# Spec Delta

## ADDED Requirements

### Requirement: Chat projection synchronization in messages

`messages` SHALL maintain a local projection of chats by consuming `ChatLifecycle` events (`ChatCreated`) from `chats`. The projection SHALL be the only source of chat data used by the service for resolving chat references; the service SHALL NOT read the `chats` service database.

#### Scenario: Message published with real chat

- **WHEN** `messages` publishes a message whose chat id references a chat already published via `ChatCreated`
- **THEN** the local `chats` projection SHALL contain that chat so the message row satisfies its foreign-key constraint.

#### Scenario: Duplicate ChatCreated ignored

- **WHEN** `messages` receives the same `ChatCreated` event again
- **THEN** it SHALL detect the duplicate by chat id and event time and SHALL NOT re-apply the change.

#### Scenario: First ChatCreated for a chat

- **WHEN** `messages` receives `ChatCreated` for a chat id absent from the local projection
- **THEN** it SHALL insert the projection row and set `last_sync_at` to the event time.