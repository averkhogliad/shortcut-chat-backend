# Proposal

## Why

The chat lifecycle event surface is ambiguous: it is not recorded whether `chats` is missing events for chat mutation operations. An audit of the current code confirms that the only chat mutation endpoint is `POST /chats` and that `ChatCreated` is already emitted through the transactional outbox. Recording this baseline prevents future work from assuming unimplemented endpoints or events exist.

## What Changes

- Record the audited current state of chat lifecycle event production; no runtime behavior changes.
- Record that `chats` currently exposes only `GET /chats`, `GET /chats/{id}`, `POST /chats`, and `GET /members`; there are no chat update, delete, or membership mutation endpoints.
- Record that the only chat lifecycle event currently produced is `ChatCreated`, emitted through the transactional outbox on the `ChatLifecycle` topic; no `ChatUpdated`, `ChatDeleted`, or membership events exist.
- Record that `messages`/`MessagePublished` is outside the chat lifecycle event scope.
- No code, REST contract, Kafka contract, dependency, or consumer changes.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none — this change records the existing state without changing spec-level behavior; the change declares `skip_specs: true`)

## Impact

- No code, API, dependency, runtime, or consumer changes.
- Documentation: record the audit result in the backend domain profile so later changes can rely on the confirmed baseline.
- Planning artifacts only in this change.
