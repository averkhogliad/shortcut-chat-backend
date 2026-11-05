# Proposal

## Why

`UserController` exposes `DELETE /users/{id}` (returns 204 on success) and `kafka-messaging` spec requires `UserDeleted` events published through the outbox. However `system/rest-api` spec does not list the DELETE endpoint in the Users REST endpoints table. The `sync-user-projection` change added `UserDeleted` to `kafka-messaging` and `architecture` specs but did not update `rest-api`.

## What Changes

- Add `DELETE /users/{id}` to the Users REST endpoints requirement in `system/rest-api`.
- Add scenarios for successful deletion (204) and unknown user (404 ProblemDetail).
- No code change; the endpoint is already implemented in `UserController`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/rest-api`: Users REST endpoints table gains `DELETE /users/{id}` with response 204.

## Impact

- Specs: `system/rest-api` — one endpoint added to the existing table, two scenarios added.
- Code: none.
- Dependencies: none.
- Consumers: none.
- Tests: none.