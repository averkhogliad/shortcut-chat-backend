# Proposal

## Why

`system/architecture` requires that `chats` and `messages` "SHALL NOT retain stale user data for new aggregates" when a user is deleted, but does not define what specifically happens for chat creation, message posting, existing chat display, or member listing. Without these rules the behaviour is ambiguous and cannot be implemented or tested consistently.

## What Changes

- `system/architecture`: add requirement "Deleted user behaviour in service operations" defining:
  - Chat creation with a deleted member SHALL be rejected.
  - Message creation by a deleted author is permitted (no restriction).
  - Existing chat responses SHALL include deleted users with a `deleted` indicator.
  - `ChatCreatedEvent.members` SHALL pass all member refs as-is, without filtering by deleted status.
  - `GET /members` SHALL exclude deleted users by default.
- `system/rest-api`: add `includeDeleted` optional query parameter to `GET /members` (default `false`); add scenario for `POST /chats` rejected with 400 when a member is deleted; document `deleted` field on `User` in chat responses (additive).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/architecture`: deleted user behaviour in chat/message operations defined.
- `system/rest-api`: `GET /members` gains optional `includeDeleted` parameter; `POST /chats` gains 400 scenario for deleted member; `User` response gains optional `deleted` field.

## Impact

- Specs: `system/architecture`, `system/rest-api`.
- Code: implementation tasks will follow this spec change; no code change in this change.
- Dependencies: none.
- Consumers: `notifications` benefits from `GET /members` filtering deleted users by default.
- Tests: implementation tasks will add tests for the new behaviour.