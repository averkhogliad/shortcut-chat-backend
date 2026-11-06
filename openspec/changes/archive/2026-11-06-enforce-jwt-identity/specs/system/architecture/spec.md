# Spec Delta

## ADDED Requirements

### Requirement: REST-facing services enforce JWT authentication

The REST-facing services `chats`, `messages`, and `notifications` SHALL validate incoming JWT access tokens as OAuth2 resource servers. The `users` service SHALL enforce JWT authentication on its own REST endpoints via its default security filter chain. `mail-sender` is a Kafka consumer with no REST surface and is not affected.

#### Scenario: chats service validates tokens

- **WHEN** `chats` receives a request to an authenticated endpoint
- **THEN** it SHALL validate the JWT via JWKs from the `users` service and extract the user id from `sub`.

#### Scenario: messages service validates tokens

- **WHEN** `messages` receives a request to an authenticated endpoint
- **THEN** it SHALL validate the JWT via JWKs from the `users` service and extract the user id from `sub`.

#### Scenario: notifications service validates tokens

- **WHEN** `notifications` receives a request to `GET /subscribe`
- **THEN** it SHALL validate the JWT from the `token` query parameter via JWKs from the `users` service and extract the user id from `sub`.

### Requirement: JWT resource server dependency

The REST-facing services `chats`, `messages`, and `notifications` MAY depend on `spring-boot-starter-oauth2-resource-server`. This library dependency SHALL NOT create a compile-time dependency between service modules. Dependency versions SHALL come from the Spring Boot BOM. The `users` service SHALL validate tokens with its existing Spring Security dependencies.

#### Scenario: chats and messages validate with the starter

- **WHEN** `chats` or `messages` validates a JWT
- **THEN** it SHALL use `spring-boot-starter-oauth2-resource-server`.

#### Scenario: No cross-service compile dependency

- **WHEN** a service adds the resource server starter
- **THEN** no service module SHALL gain a compile dependency on another service module.

### Requirement: Shared identity extraction helper

The `common` module SHALL provide one shared helper that reads the authenticated user id as a `UUID` from the JWT `sub` claim through the security context. `common` SHALL depend only on `spring-security-core` and `spring-security-oauth2-jose` for this helper and SHALL NOT ship Spring Security auto-configuration or a new module.

#### Scenario: Service resolves current user id

- **WHEN** a service needs the caller id
- **THEN** it SHALL read it through the common helper from the JWT `sub` claim.

#### Scenario: Common adds no auto-configuration

- **WHEN** `common` is on the classpath
- **THEN** it SHALL NOT activate Spring Security auto-configuration.

#### Scenario: Non-UUID subject rejected

- **WHEN** the `sub` claim is not a valid UUID
- **THEN** the request SHALL fail with a `4xx` status and SHALL NOT return `5xx`.

## MODIFIED Requirements

### Requirement: Message creation permits deleted authors

`messages` SHALL permit `POST /messages` by a deleted author without additional checks. The author SHALL be identified by the JWT `sub` claim from the `Authorization: Bearer` header; the dev-only `X-User-Id` header SHALL NOT identify the caller.

#### Scenario: Message creation by deleted author permitted

- **WHEN** `POST /messages` is called with a Bearer JWT whose `sub` references a deleted user
- **THEN** the message SHALL be created and `MessagePublished` SHALL be published.
