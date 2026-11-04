# Spec Delta

## MODIFIED Requirements

### Requirement: Outbox event headers are customizable

The starter SHALL set event headers through a header provider. The default provider SHALL set the standard headers from `system/kafka-messaging` plus optional per-route static headers. A consuming service MAY supply its own header provider bean to override the default. The header provider interface SHALL live in the starter; the `X-Event-*` header-name constants SHALL live in `common` and the starter SHALL delegate to them.

#### Scenario: Default headers are set

- **WHEN** the default header provider builds headers for an entry
- **THEN** the entry SHALL carry the five standard headers derived from the record id, route event name, message version, publish time, and application name.

#### Scenario: Per-route static headers are merged

- **WHEN** a route declares static headers and the default provider is used
- **THEN** those headers SHALL be added on top of the standard headers, and the standard headers SHALL not be overwritten by static configuration.

#### Scenario: Consumer overrides the header provider

- **WHEN** a consuming service defines its own header provider bean
- **THEN** the starter SHALL use that bean instead of the default and SHALL not register the default.
