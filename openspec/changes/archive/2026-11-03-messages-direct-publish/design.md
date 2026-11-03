# Design

## Context

`messages` publishes `MessagePublished` via `KafkaTemplate.send` in a `transaction { afterCommit { ... } }` callback. `users` and `chats` publish through `tx-outbox-starter`: `OutboxEmitter.emit` writes an outbox row in the current transaction, and a scheduled relay claims, publishes, and cleans it with at-least-once delivery. `messages` deliberately does not use the outbox: message posting is interactive and the relay's `fixed-delay` scheduling adds latency between commit and publish that is undesirable for response time.

## Goals / Non-Goals

**Goals:**
- Record the direct-publish decision as a normative requirement in `system/runtime-guarantees`.
- Make the outbox requirement scope (`users` and `chats` only) explicit.
- Move the architectural decision out of Project Rules into Specs.

**Non-Goals:**
- No code change; `messages` already implements direct publish.
- No outbox migration, no `tx-outbox-starter` dependency, no `message_outbox` schema in `messages`.
- No new event types, no consumer changes, no new projections, no idempotency state.
- No change to the `ChatLifecycle` topic, event name, payload (`Message`), or headers.
- No change to `notifications` SSE semantics.

## Decisions

### 1. Keep direct publish for `messages`

`messages` SHALL publish `MessagePublished` directly from a post-commit callback without the transactional outbox.

**Why:** Lower publish latency for interactive message creation. The outbox relay publishes on a schedule, not immediately after commit; for chat messages this delay is not acceptable.

**Alternative:** Migrate `messages` to `tx-outbox-starter` for at-least-once delivery. Rejected: the latency cost is not justified for `MessagePublished`; event loss on crash is accepted.

### 2. Record the decision as a requirement, not a "Current Behavior" note

Promote the decision from a non-normative "Current Behavior" note to a `### Requirement` in `system/runtime-guarantees`, with scenarios that make the at-most-once trade-off explicit.

**Why:** A deliberate architecture decision must be normative and testable, not an observational note that can drift.

### 3. Scope the outbox requirement to `users` and `chats`

The existing "Outbox write and processing" requirement already names `users` and `chats`. This change makes the exclusion of `messages` explicit by adding the dedicated direct-publish requirement.

### 4. Remove the architectural prohibition from Project Rules

`.agents/rules/architecture/module-boundaries.md` previously forbade adding an outbox to `messages`. Rules are agent constraints, not architecture decisions; the decision now lives in Specs.

## Risks / Trade-offs

- [Event loss on crash after commit] → Accepted for latency. `notifications` SSE is live-only with no replay; consumers are expected to be idempotent. No new idempotency state is added in `messages`.
- [No retry / dead-letter] → Direct send has no relay retry; a failed send after commit is not retried. Accepted; consistent with the latency goal.
- [Decision recorded only in spec] → The rule removal is a documentation edit; no runtime impact.

## Migration Plan

1. Add the direct-publish requirement to `system/runtime-guarantees` (via this change's delta).
2. Remove the superseded "Current Behavior" note about `messages` from the main spec.
3. Remove the prohibition bullet from `.agents/rules/architecture/module-boundaries.md`.
4. No data or code migration; `messages` runtime is unchanged.

## Open Questions

(none)