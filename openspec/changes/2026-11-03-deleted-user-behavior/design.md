# Design

## Context

`chats` and `messages` maintain local user projections by consuming `UserLifecycle` events. When `UserDeleted` arrives, the projection row is marked `deleted=true` (not removed). The architecture spec says "SHALL NOT retain stale user data for new aggregates" but does not define:

- What happens when a new chat includes a deleted user as a member.
- What happens when a deleted user posts a message.
- How existing chats display members who were deleted after the chat was created.
- Whether `GET /members` includes or excludes deleted users.

## Goals / Non-Goals

**Goals:**

- Define explicit behaviour for each operation involving deleted users.
- Keep changes additive to REST and Kafka contracts.

**Non-Goals:**

- Hard-removing projection rows on `UserDeleted` (current soft-delete stays).
- Changing `UserDeleted` event payload or `UserLifecycle` topic contract.
- Adding authentication or authorization for `includeDeleted`.

## Decisions

### 1. Reject chat creation with a deleted member (400)

`POST /chats` SHALL reject the request with 400 `ProblemDetail` if any member ID in the request references a user marked `deleted=true` in the local projection.

**Why:** A new chat aggregate must not include stale user data. Rejecting is explicit and prevents silent data loss (members the caller expects are not silently dropped).

**Alternative:** Silently exclude deleted members. Rejected: the caller would not know a member was dropped, leading to chats with fewer members than requested without feedback.

### 2. Permit message creation by a deleted author

`POST /messages` with `X-User-Id` pointing to a deleted user SHALL be permitted. No additional check is required.

**Why:** Accepted trade-off. Message authorship is lower-stakes than chat membership; the author reference is a UUID, not stale profile data displayed in the chat. Blocking would add a DB read to every message post for a rare edge case.

### 3. Show deleted users in existing chat responses with `deleted` marker

`ChatDetails.members` and `ChatListItem.owner` SHALL include deleted users with a `deleted: true` field on the `User` response object. Active users have `deleted: false` (or the field is absent).

**Why:** Existing chats retain historical context. Removing deleted users from display would hide information the caller needs (e.g., understanding who was in a chat). The marker makes the status explicit.

**Additive:** The `deleted` field is optional with a default of `false`, so existing clients are not broken.

### 4. `GET /members` filters deleted users by default; optional `includeDeleted` parameter

`GET /members?chatId=...` SHALL exclude deleted users from the `members` collection by default. An optional `includeDeleted=true` query parameter SHALL include them.

**Why:** `notifications` calls `GET /members` to resolve who receives SSE events. Deleted users should not receive notifications. The `includeDeleted` parameter allows admin or debug access when needed.

**Additive:** The parameter is optional with a default of `false`, so existing callers are unaffected.

### 5. `ChatCreatedEvent.members` passes all member refs as-is

The event payload SHALL contain all member refs from the chat, without filtering by deleted status.

**Why:** Since chat creation with deleted members is rejected (Decision 1), this scenario should not occur in normal flow. For edge cases (e.g., user deleted between request and event publish), the event should reflect the actual chat membership. Consumer behaviour for deleted members in events is determined by the consumer, not by filtering at the producer.

## Risks / Trade-offs

- [Deleted author posts messages] → Accepted. The message references a deleted user by UUID; `notifications` resolves membership via `GET /members` which filters deleted users, so the deleted author will not receive SSE for that chat (unless they are a member of other chats).
- [400 for deleted member may surprise callers] → Mitigated by `ProblemDetail` response body explaining the rejection.
- [`includeDeleted` parameter has no authorization] → Accepted for now; authorization is not implemented in the system.

## Open Questions

(none)