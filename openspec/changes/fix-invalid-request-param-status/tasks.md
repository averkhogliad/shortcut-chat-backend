# Tasks

## 1. Error handling in MVC services

- [x] 1.1 Add the `MethodArgumentTypeMismatchException` handler to `chats` `EndpointExceptionAdvice`; verify `GET /members?chatId=invalid-uuid` returns `400` with `ProblemDetail` via `ChatMembersEndpointTest`
- [x] 1.2 Add the same handler to `users` `EndpointExceptionAdvice`; verify by running `users` tests
- [x] 1.3 Add the same handler to `messages` `EndpointExceptionAdvice`; verify by running `messages` tests

## 2. Verification

- [x] 2.1 Run `.\mvnw.cmd -pl chats,users,messages -am test` and confirm the new behavior and no regressions; record Docker/Testcontainers limits if the environment blocks the run

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, so the delta reaches `openspec/specs/system/rest-api/spec.md`.
- Verify the archived result.
