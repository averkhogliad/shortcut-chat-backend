# Tasks

## 1. Spec delta

- [ ] 1.1 Verify `openspec validate --changes deleted-user-behavior --strict` passes.
- [ ] 1.2 Sync architecture delta to `openspec/specs/system/architecture/spec.md`.
- [ ] 1.3 Sync rest-api delta to `openspec/specs/system/rest-api/spec.md`.
- [ ] 1.4 Verify `openspec validate --specs` passes.

## 2. Implementation (separate tasks, not in this change)

- [ ] 2.1 `chats`: add `findByIdIsInAndDeletedFalse` to `UserRepository`; use in `ChatService.create` and `findById` to filter deleted users from member display. Reject `POST /chats` with `EntityResult.Invalid` if any requested member is deleted.
- [ ] 2.2 `chats`: add `deleted: Boolean = false` field to `User` response model; populate from `UserEntity.deleted` in `UserConverter`.
- [ ] 2.3 `chats`: add `includeDeleted` query parameter to `ChatMembersController` (`GET /members`); default `false` (filter deleted); `true` includes deleted users.
- [ ] 2.4 `chats`: add test — `POST /chats` with deleted member returns 400 `ProblemDetail`.
- [ ] 2.5 `chats`: add test — `GET /chats/{id}` shows deleted member with `deleted: true`.
- [ ] 2.6 `chats`: add test — `GET /members` excludes deleted by default, includes with `includeDeleted=true`.
- [ ] 2.7 `messages`: no code change required (deleted author permitted).

## 3. Archive

- [ ] 3.1 Archive the change after spec sync (`openspec/changes/archive/2026-11-03-deleted-user-behavior`).