# Spec Delta

## ADDED Requirements

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