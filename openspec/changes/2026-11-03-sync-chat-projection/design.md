# Design

## Context

`messages` has a local `chats` table with `id`, `name`, `last_sync_at`, `is_deleted` and a FK `messages.chat_id → chats.id`. `ChatEntity` maps this table with a `@MappedCollection` for `chat_members`. `ChatRepository` declares `findById` and `save`. No code populates this table — it exists only in migrations and entity declarations.

`chats` publishes `ChatCreated` to `ChatLifecycle` via the transactional outbox. The event payload is `ChatCreatedEvent { id, name, owner, members, createdAt }`. Currently only `notifications` consumes `ChatLifecycle` (for SSE filtering). `messages` does not consume it.

The `sync-user-projection` change established the pattern: `UserLifecycleTypeResolver` → `UserLifecycleEventsHandler` → `UserProjectionService` with `last_sync_at` idempotency. This change replicates that pattern for chat projection.

## Goals / Non-Goals

**Goals:**

- `messages` consumes `ChatCreated` from `ChatLifecycle` and maintains a local `chats` projection.
- The projection satisfies the FK `messages.chat_id → chats.id` so `POST /messages` succeeds for synced chats.
- Idempotent and ordered by `last_sync_at`, same as user projection.

**Non-Goals:**

- Populating `chat_members` in `messages` — the messages service does not read or write `chat_members`; the FK it needs is `messages.chat_id → chats.id` only.
- Handling `ChatUpdated` or `ChatDeleted` — no such events exist (baseline recorded in `record-chat-lifecycle-state`).
- Changing `ChatCreatedEvent` payload, topic, or headers.
- Adding `messages` as a consumer of `MessagePublished` — `messages` is the producer.

## Decisions

### 1. Consume `ChatCreated` only

`messages` SHALL consume `ChatCreated` from `ChatLifecycle`. No other chat lifecycle events exist. The `ChatLifecycleTypeResolver` in `messages` SHALL resolve `ChatCreated` → `ChatCreatedPayload` and fall back to `Map` for unknown event names.

**Why:** Matches the `UserLifecycleTypeResolver` pattern. Unknown events are logged and ignored, not crashed on.

### 2. Projection inserts chat row only, not chat_members

`ChatProjectionService` SHALL insert or update the `chats` row (`id`, `name`, `last_sync_at`, `is_deleted=false`). It SHALL NOT insert `chat_members` rows.

**Why:** The messages service needs the `chats` row for FK satisfaction on `messages.chat_id`. It does not query `chat_members`. Inserting `chat_members` would require all member users to already exist in the local `users` table (FK `chat_members.user_id → users.id`), creating an ordering dependency that is unnecessary for message creation.

**Implementation note:** `ChatEntity` has `@MappedCollection members: Set<MemberEntity>`. When inserting via `jdbcAggregateTemplate.insert`, clear `members = emptySet()` to avoid Spring Data JDBC attempting to insert member rows.

### 3. Idempotency via `last_sync_at`

Same pattern as user projection: on `ChatCreated`, load the chat by `id`. If found and `eventTime <= lastSyncAt`, ignore. If found and newer, update `name` and `lastSyncAt`. If not found, insert with `lastSyncAt = eventTime`.

**Why:** Outbox delivery is at-least-once; duplicates must not corrupt the projection. Event time comes from `X-Published-At` header.

### 4. `@LastModifiedDate` on `ChatEntity.lastSyncAt`

`ChatEntity` in `messages` has `@LastModifiedDate @Column("last_sync_at")`. This annotation may overwrite the manually set `lastSyncAt` on save. The user projection removed this annotation from `UserEntity` for the same reason. This change SHALL remove `@LastModifiedDate` from `ChatEntity.lastSyncAt` in `messages`.

**Why:** The projection service manages `lastSyncAt` manually. `@LastModifiedDate` interferes with this control.

### 5. Separate consumer group

`messages` already declares `spring.kafka.consumer.group-id: messages-app` and `auto-offset-reset: earliest` in `application.yaml`. The `ChatLifecycle` listener SHALL use the same group ID. This means `messages` consumes `ChatLifecycle` independently from `notifications` (which uses group `notifications-app`).

**Why:** Each service maintains its own offset and projection state. No shared consumer group.

## Risks / Trade-offs

- [ChatCreated arrives before UserCreated for members] → Not a problem: chat projection does not insert `chat_members`, so no FK on `users.id` is triggered. The `chats` row is inserted independently.
- [No ChatUpdated/ChatDeleted events] → Chat projection may become stale if chat name changes or chat is deleted. Accepted: no mutation endpoints exist (baseline). Future changes will add events and projection handling.
- [`@LastModifiedDate` removal] → If any other code depends on `@LastModifiedDate` on `ChatEntity`, it will break. Checked: no other code saves `ChatEntity` in `messages` except the projection service (to be added). No break.

## Open Questions

(none)