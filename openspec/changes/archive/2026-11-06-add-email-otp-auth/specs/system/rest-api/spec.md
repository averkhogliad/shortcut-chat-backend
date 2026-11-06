# Spec Delta

## MODIFIED Requirements

### Requirement: Users REST endpoints

The `users` service SHALL expose these endpoints:

| Method | Path | Response |
|---|---|---|
| `GET` | `/users/{id}` | `User` |
| `GET` | `/users/by-email?email=...` | `User` |
| `POST` | `/users` | `User` |
| `PUT` | `/users/{id}` | `User` |
| `DELETE` | `/users/{id}` | 204 No Content |

#### Scenario: Unknown user

- **WHEN** `GET /users/{id}` is called for a missing user
- **THEN** the response SHALL use the `ProblemDetail` error format.

#### Scenario: Delete user

- **WHEN** `DELETE /users/{id}` succeeds
- **THEN** the response status SHALL be `204`.

#### Scenario: Delete unknown user

- **WHEN** `DELETE /users/{id}` is called for a missing user
- **THEN** the response status SHALL be `404`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: Email is immutable

- **WHEN** `PUT /users/{id}` is called with an `email` field that differs from the existing user's email
- **THEN** the response status SHALL be `400`
- **AND** the response body SHALL be a `ProblemDetail`.

#### Scenario: POST /users requires admin role

- **WHEN** `POST /users` is called without an admin role
- **THEN** the response status SHALL be `403`.

## ADDED Requirements

### Requirement: Auth REST endpoints

The `users` service SHALL expose these authentication endpoints:

| Method | Path | Response |
|---|---|---|
| `POST` | `/auth/code` | 202 Accepted |
| `POST` | `/oauth2/token` | OAuth2 Token Response |
| `GET` | `/.well-known/openid-configuration` | OIDC Discovery Document |
| `GET` | `/oauth2/jwks` | JWK Set |
| `GET` | `/userinfo` | UserInfo Claims |

#### Scenario: Request OTP code

- **WHEN** `POST /auth/code` is called with a valid email
- **THEN** the response status SHALL be `202 Accepted`.

#### Scenario: Request OTP code rate limited

- **WHEN** `POST /auth/code` is called for the same email more than once within 60 seconds
- **THEN** the response status SHALL be `429`.

#### Scenario: Token endpoint with email OTP grant

- **WHEN** `POST /oauth2/token` is called with `grant_type=urn:shortcut:oauth:grant-type:email-otp`, `email`, and `code`
- **THEN** the response SHALL contain `access_token`, `id_token`, `refresh_token`, `token_type=Bearer`, and `expires_in`.

#### Scenario: OIDC discovery

- **WHEN** `GET /.well-known/openid-configuration` is called
- **THEN** the response SHALL contain `issuer`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, and `grant_types_supported` including `urn:shortcut:oauth:grant-type:email-otp`.