# Tasks

## 1. Record the audited chat lifecycle state

- [x] 1.1 Re-verify the audit against the current code: `ChatController` exposes only `GET /chats`, `GET /chats/{id}`, `POST /chats`; `ChatService.create` emits `ChatCreated` via `OutboxEmitter`; `chats/application.yaml` routes only `ChatCreated` to `ChatLifecycle`; no `ChatUpdated`, `ChatDeleted`, or membership events exist. Verify by reading the four files and confirming the findings match this change's proposal.
- [x] 1.2 Add the confirmed chat lifecycle baseline to the "Текущее состояние и ограничения" section of `.agents/context/backend.md`: the only chat mutation endpoint and event is create/`ChatCreated`, no update/delete/member mutation endpoints or events exist, and `messages`/`MessagePublished` is excluded from the chat event scope. Verify the new text is present and no source code, POM, migration, or application configuration changed.
- [x] 1.3 Run `openspec validate --change record-chat-lifecycle-state --strict` and verify it passes. Run `git diff --stat` and verify only the change artifacts and `.agents/context/backend.md` are modified by this change.

## Workflow follow-up

- Archive the change after review.
- Re-check the recorded baseline when chat mutation endpoints are introduced; that future change owns the corresponding events.
