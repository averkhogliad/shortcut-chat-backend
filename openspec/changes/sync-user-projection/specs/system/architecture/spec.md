# Spec Delta

## ADDED Requirements

### Requirement: User projection synchronization in chats and messages

`chats` and `messages` SHALL maintain a local projection of users by consuming `UserLifecycle` events (`UserCreated`, `UserUpdated`, `UserDeleted`) from `users`. The projection SHALL be the only source of user data used by the service for resolving members, owners, and authors; the service SHALL NOT read the `users` service database.

#### Scenario: Chat created with real users

- **WHEN** `chats` creates a chat whose member ids reference users already published via `UserCreated`
- **THEN** the local `users` projection SHALL contain those users so the chat and member rows satisfy foreign-key constraints.

#### Scenario: Message published with real author

- **WHEN** `messages` publishes a message whose author id references a user already published via `UserCreated`
- **THEN** the local `users` projection SHALL contain that user so the message row satisfies its foreign-key constraint.

#### Scenario: User updated propagates to projections

- **WHEN** `users` publishes `UserUpdated`
- **THEN** `chats` and `messages` SHALL update the corresponding local projection row.

#### Scenario: User deleted propagates to projections

- **WHEN** `users` publishes `UserDeleted`
- **THEN** `chats` and `messages` SHALL remove or mark the corresponding local projection row and SHALL NOT retain stale user data for new aggregates.