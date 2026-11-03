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