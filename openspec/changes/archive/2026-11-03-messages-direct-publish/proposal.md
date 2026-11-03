# Proposal

## Why

`messages` publishes `MessagePublished` directly to `KafkaTemplate` in a post-commit (`afterCommit`) callback, without the transactional outbox that `users` and `chats` use. This is a deliberate architecture decision: message creation is interactive and latency-sensitive, and the outbox relay introduces a scheduling gap between commit and publish. The accepted trade-off is at-most-once delivery — if the process crashes after the aggregate commit and before the send completes, the event is lost.

The decision currently lives only as a stale "Current Behavior" note in `system/runtime-guarantees` and as an architectural prohibition in `.agents/rules/architecture/module-boundaries.md`. Rules are agent constraints, not architecture decisions; the decision must be recorded as a requirement in the specs.

## What Changes

- Record the decision in `system/runtime-guarantees` as a requirement: `messages` SHALL publish `MessagePublished` directly after commit without the transactional outbox; delivery is at-most-once; the trade-off is accepted for lower publish latency.
- Scope the existing outbox requirement to `users` and `chats` only (it already is; this makes the exclusion of `messages` explicit).
- Remove the architectural prohibition from `.agents/rules/architecture/module-boundaries.md` (rules are not architecture decisions).
- No code change: `messages` already implements direct publish. No new endpoints, event types, consumers, dependencies, migrations, or tests.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `system/runtime-guarantees`: `messages` direct-publish decision recorded as a requirement; outbox requirement scope clarified to `users` and `chats`.

## Impact

- Specs: `system/runtime-guarantees` — requirement added; superseded "Current Behavior" note about `messages` removed.
- Rules: `.agents/rules/architecture/module-boundaries.md` — architectural prohibition bullet removed.
- Code: none.
- Dependencies: none (`messages` does not depend on `tx-outbox-starter`).
- Consumers: none; `notifications` keeps resolving `MessagePublished` by `X-Event-Name`.
- Tests: none.