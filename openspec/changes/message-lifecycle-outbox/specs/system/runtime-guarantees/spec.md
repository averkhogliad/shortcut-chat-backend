# Spec Delta

## MODIFIED Requirements

### Requirement: Outbox write and processing

`users`, `chats`, and `messages` SHALL write the outbox record in the same transaction as the aggregate change. The outbox handler SHALL publish pending records and mark them published.

#### Scenario: Pending outbox record

- **WHEN** the outbox handler processes a pending record
- **THEN** it SHALL publish the record to its target topic and mark it published.

## ADDED Requirements

### Requirement: Message published through the transactional outbox

`messages` SHALL publish `MessagePublished` through the transactional outbox in the same transaction as the message aggregate change, with at-least-once delivery, consistent with `ChatCreated` and `UserLifecycle` events.

#### Scenario: Message created transactionally

- **WHEN** `messages` creates a message aggregate
- **THEN** the aggregate change and the `MessagePublished` outbox record SHALL be committed in one transaction.

#### Scenario: Crash after commit before direct send

- **WHEN** the process crashes after the message aggregate commit but before the event is delivered
- **THEN** the outbox record SHALL remain pending and SHALL be published by the outbox handler on recovery.