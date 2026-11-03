# Spec Delta

## MODIFIED Requirements

### Requirement: Outbox write and processing

`users` and `chats` SHALL write the outbox record in the same transaction as the aggregate change. The outbox relay SHALL claim pending records, publish them through an `OutboxPublisher`, and transition them through `NEW`, `CLAIMED`, `RETRY_WAIT`, `PUBLISHED`, and `DEAD` states. Records that exceed the configurable retry limit SHALL transition to `DEAD`. Claimed records SHALL be leased to a relay owner until publication or lease expiry, and expired leases SHALL be recovered. Terminal records (`PUBLISHED`, `DEAD`) SHALL be cleaned by retention.

#### Scenario: Pending outbox record

- **WHEN** the relay processes a pending record
- **THEN** it SHALL publish the record to its target topic and transition it to `PUBLISHED`.

#### Scenario: Pending record is claimed and published

- **WHEN** the relay claims a `NEW` record and the publisher succeeds
- **THEN** the record SHALL transition to `PUBLISHED`.

#### Scenario: Publish failure schedules retry

- **WHEN** publishing a claimed record fails and the attempt count is below the limit
- **THEN** the record SHALL transition to `RETRY_WAIT` with exponential backoff up to `maxAttempts`, after which it SHALL transition to `DEAD`.

#### Scenario: Expired lease is recovered

- **WHEN** a claimed record's lease has expired
- **THEN** the relay SHALL recover it for re-publish.

#### Scenario: Terminal records are cleaned

- **WHEN** cleanup runs and a `PUBLISHED` or `DEAD` record is older than its retention
- **THEN** the record SHALL be deleted.