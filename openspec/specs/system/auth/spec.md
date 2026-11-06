# system/auth Specification

## Purpose
Passwordless authentication via email one-time codes. The `users` service acts as an OIDC provider, issuing JWT access and ID tokens after email OTP verification. Users are auto-registered on first successful verification.

## Requirements

### Requirement: Email OTP request

The `users` service SHALL expose `POST /auth/code` accepting an `email` field. The service SHALL generate a 6-digit random code, store it in Redis with a TTL of 5 minutes and an attempt counter initialised to 3, and emit a `SendEmail` outbox event containing the code. The response SHALL always be `202 Accepted`, regardless of whether the user exists, to avoid revealing registered emails.

#### Scenario: Request OTP for existing user

- **WHEN** `POST /auth/code` is called with an email that belongs to an existing user
- **THEN** the service SHALL generate a 6-digit code, store it in Redis with TTL 300 seconds, emit `SendEmail` via outbox, and return `202`.

#### Scenario: Request OTP for unknown email

- **WHEN** `POST /auth/code` is called with an email that no user has
- **THEN** the service SHALL generate a 6-digit code, store it in Redis, emit `SendEmail`, and return `202`. No user SHALL be created at this stage.

#### Scenario: Rate limit on OTP requests

- **WHEN** `POST /auth/code` is called for the same email more than once within 60 seconds
- **THEN** the service SHALL return `429 Too Many Requests` without generating or storing a new code.

### Requirement: Email OTP verification and token issuance

The `users` service SHALL expose `POST /oauth2/token` accepting a custom grant type `urn:shortcut:oauth:grant-type:email-otp` with `email` and `code` parameters. The service SHALL verify the code against Redis, decrement the attempt counter on mismatch, and invalidate the code after 3 failed attempts. On success the service SHALL issue a JWT access token, an ID token, and a refresh token.

#### Scenario: Successful verification

- **WHEN** `POST /oauth2/token` is called with a valid email and correct code
- **THEN** the service SHALL return an access token, an ID token, and a refresh token. The code SHALL be deleted from Redis.

#### Scenario: Incorrect code

- **WHEN** `POST /oauth2/token` is called with a valid email but incorrect code
- **THEN** the service SHALL decrement the attempt counter in Redis and return an error. The code SHALL remain valid until attempts reach zero.

#### Scenario: Code exhausted

- **WHEN** the attempt counter reaches zero
- **THEN** the code SHALL be invalidated and subsequent verification attempts for that email SHALL return an error indicating the code is no longer valid.

#### Scenario: Expired code

- **WHEN** `POST /oauth2/token` is called after the code TTL has elapsed
- **THEN** the service SHALL return an error indicating the code has expired.

### Requirement: Auto-registration on first verification

On successful OTP verification, if no user exists for the given email, the `users` service SHALL create a `User` with `id` set to a generated UUID, `name` set to the part of the email before `@`, and `email` set to the verified email. The service SHALL publish a `UserCreated` event via the transactional outbox in the same transaction as the user creation.

#### Scenario: First login creates user

- **WHEN** OTP verification succeeds for an email with no existing user
- **THEN** the service SHALL create a user, publish `UserCreated` via outbox, and issue tokens with `sub` equal to the new user id.

#### Scenario: Returning user does not create duplicate

- **WHEN** OTP verification succeeds for an email that already has a user
- **THEN** the service SHALL NOT create a new user and SHALL issue tokens with `sub` equal to the existing user id.

### Requirement: JWT token claims

The access token SHALL include `sub` (user id as UUID string), `iss` (issuer URL), `aud` (client id), `exp`, `iat`, and `scope` claims. The ID token SHALL additionally include `email` and `name` claims.

#### Scenario: Access token claims

- **WHEN** an access token is issued
- **THEN** it SHALL contain `sub` equal to the user id, `iss` equal to the configured issuer URL, and `scope` including `openid`.

#### Scenario: ID token claims

- **WHEN** an ID token is issued
- **THEN** it SHALL contain `sub`, `email`, and `name` claims in addition to standard OIDC claims.

### Requirement: OIDC provider endpoints

The `users` service SHALL expose standard OIDC discovery, JWKs, and userinfo endpoints as provided by Spring Authorization Server.

#### Scenario: Discovery endpoint

- **WHEN** `GET /.well-known/openid-configuration` is called
- **THEN** the response SHALL contain `issuer`, `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, and supported grant types.

#### Scenario: JWKs endpoint

- **WHEN** `GET /oauth2/jwks` is called
- **THEN** the response SHALL contain the public RSA key used to sign tokens.

#### Scenario: Userinfo endpoint

- **WHEN** `GET /userinfo` is called with a valid access token
- **THEN** the response SHALL contain `sub`, `email`, and `name` for the authenticated user.

### Requirement: JWK persistence

The `users` service SHALL persist the RSA signing keypair to a file on disk. On startup, the service SHALL load the key from the file. If the file does not exist, the service SHALL generate a new keypair and write it to the file. Tokens SHALL remain valid across service restarts.

#### Scenario: Key loaded from file on startup

- **WHEN** the `users` service starts and the JWK file exists
- **THEN** the service SHALL load the existing keypair and use it for signing tokens.

#### Scenario: Key generated on first startup

- **WHEN** the `users` service starts and the JWK file does not exist
- **THEN** the service SHALL generate a new RSA keypair, write it to the file, and use it for signing tokens.

#### Scenario: Tokens survive restart

- **WHEN** the `users` service restarts after issuing tokens
- **THEN** previously issued tokens SHALL remain valid because the signing key is loaded from the persisted file.

### Requirement: Refresh token

The `users` service SHALL issue refresh tokens with a TTL of 9 hours. A refresh token SHALL be exchangeable for a new access token and ID token at `POST /oauth2/token` using the standard `refresh_token` grant type.

#### Scenario: Refresh access token

- **WHEN** `POST /oauth2/token` is called with `grant_type=refresh_token` and a valid refresh token
- **THEN** the service SHALL issue a new access token and ID token.
