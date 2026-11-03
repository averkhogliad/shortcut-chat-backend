# Spec Delta

## MODIFIED Requirements

### Requirement: Each service owns its data

Each service that persists data SHALL own its database schema and SHALL NOT read or write another service's tables. Services SHALL NOT share entities, repositories, migrations, or a datasource. A service MAY use an additional ephemeral store (Redis) for non-persistent data, provided it namespaces its keys and does not share them with other services.

#### Scenario: Cross-service data access

- **WHEN** a service needs data owned by another service
- **THEN** it SHALL receive the data through Kafka or the approved HTTP integration.

#### Scenario: Ephemeral store is namespaced

- **WHEN** a service uses Redis for ephemeral data
- **THEN** it SHALL namespace its keys with a service-specific prefix and SHALL NOT read or write keys belonging to another service.

## ADDED Requirements

### Requirement: Users service as OIDC provider

The `users` service SHALL act as an OpenID Connect provider. It SHALL issue JWT access tokens, ID tokens, and refresh tokens after successful email OTP verification. It SHALL expose standard OIDC endpoints: discovery, JWKs, token, and userinfo. Token validation by other services or a gateway is out of scope for this requirement.

#### Scenario: Token issuance after OTP verification

- **WHEN** a client completes email OTP verification at the token endpoint
- **THEN** the `users` service SHALL issue a JWT access token and ID token containing the user id as `sub`.

#### Scenario: Other services validate tokens independently

- **WHEN** another service or gateway needs to validate a token
- **THEN** it SHALL validate the JWT using the JWKs published by the `users` service. The `users` service SHALL NOT provide a token introspection endpoint.

### Requirement: OTP storage in Redis

The `users` service SHALL store email OTP codes in Redis under keys `users:otp:{email}` with a TTL of 300 seconds. Each entry SHALL track the code and remaining attempt count. The service SHALL NOT store OTP codes in PostgreSQL.

#### Scenario: OTP stored with TTL

- **WHEN** the `users` service generates an OTP code
- **THEN** it SHALL store the code in Redis with a TTL of 300 seconds and an attempt count of 3.

#### Scenario: OTP expired automatically

- **WHEN** an OTP code's TTL elapses
- **THEN** Redis SHALL automatically remove the key and the code SHALL no longer be valid.