# Spec Delta

## ADDED Requirements

### Requirement: OTP code lifecycle

An OTP code SHALL be valid for 300 seconds (5 minutes) from generation. A code SHALL allow at most 3 verification attempts. After 3 failed attempts the code SHALL be invalidated regardless of TTL. A successful verification SHALL delete the code immediately.

#### Scenario: Code expires after TTL

- **WHEN** 300 seconds elapse since code generation without verification
- **THEN** the code SHALL no longer be valid and verification SHALL return an expired-code error.

#### Scenario: Code invalidated after 3 failed attempts

- **WHEN** 3 verification attempts with incorrect codes are made
- **THEN** the code SHALL be invalidated and subsequent attempts SHALL return an invalid-code error.

#### Scenario: Code deleted on success

- **WHEN** a verification attempt succeeds
- **THEN** the code SHALL be immediately deleted and SHALL NOT be reusable.

### Requirement: OTP request rate limiting

The `users` service SHALL allow at most 1 OTP request per email within 60 seconds. Subsequent requests within that window SHALL return `429 Too Many Requests` without generating or storing a new code.

#### Scenario: Rate limit enforced

- **WHEN** a second `POST /auth/code` request is made for the same email within 60 seconds
- **THEN** the response SHALL be `429` and no new code SHALL be generated or sent.

#### Scenario: Rate limit window expires

- **WHEN** 60 seconds have elapsed since the last OTP request for an email
- **THEN** a new request SHALL be accepted and a new code generated.

### Requirement: Auto-registration transaction boundary

When auto-registration occurs on successful OTP verification, the user creation and the `UserCreated` outbox record SHALL be committed in one transaction. The JWT SHALL be issued after the transaction commits.

#### Scenario: Auto-registration is transactional

- **WHEN** OTP verification succeeds for an unknown email and a new user is created
- **THEN** the user insert and the `UserCreated` outbox record SHALL be committed in one transaction.

#### Scenario: Token issued after commit

- **WHEN** auto-registration transaction commits
- **THEN** the JWT access token, ID token, and refresh token SHALL be issued with `sub` equal to the new user id.