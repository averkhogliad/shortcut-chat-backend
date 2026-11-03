# Grill-Later Backlog

Items deferred for future design exploration. Not spec'd, not planned, not in scope for current changes. Each needs a grill session before becoming a change.

## SSE replay

**From:** domain-data-flow-report G-13, CAND-09
**Problem:** `notifications` exposes a live-only SSE stream. If a client disconnects and reconnects, events published during the gap are lost. No replay mechanism exists.
**Questions to grill:** Is replay needed? Source of recovery (Kafka offsets, DB snapshot)? Retention window? Contract for `GET /subscribe` on reconnect?

## Notifications fallback when chats is unavailable

**From:** domain-data-flow-report G-14, CAND-10
**Problem:** `notifications` enriches events with chat membership via `GET /members` from `chats`. If `chats` is down, enrichment fails and the event stream breaks. No fallback.
**Questions to grill:** Use `members` from `ChatCreatedEvent` payload as fallback? Retry with backoff? Drop the event? Deliver without enrichment? Does the fallback violate the sync-call architecture constraint?

## Mail-sender retry and DLQ

**From:** domain-data-flow-report G-15, CAND-11
**Problem:** `mail-sender` stores `FAILED` on send error and does not rethrow. No retry, no dead-letter queue. Failed emails are silently lost.
**Questions to grill:** Retry policy (attempts, backoff)? DLQ topic or DB table? Should `FAILED` status trigger a scheduled retry? Interaction with Redis idempotency state?

## Chat update endpoint
                                                    

**From:** domain-data-flow-report G-12, `ChatController.kt:49` TODO
**Problem:** No `PUT /chats/{id}` endpoint. Chat name cannot be changed after creation. `ChatController` has `// TODO: update method`.
**Questions to grill:** What fields are editable (name only? members?)? Need `ChatUpdated` event? Who can update (owner only? admin?)? Projection sync in `messages`?

## Email template engine

**From:** `SendCreatedUserNotificationMessageFactory.kt:14` TODO
**Problem:** Email body is hardcoded string interpolation: `"Hello ${user.name}. Welcome to our system"`. No template engine.
**Questions to grill:** Which template engine (Thymeleaf, Freemarker, Mustache)? Where do templates live (resources, DB, external)? Is this needed at all for a course project?
