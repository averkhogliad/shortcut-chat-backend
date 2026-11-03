# Proposal

## Why

Requests with a malformed typed parameter (query, path, or `X-User-Id` header) return `500` instead of `400`. `@ExceptionHandler(Throwable::class)` in each service's `EndpointExceptionAdvice` catches `MethodArgumentTypeMismatchException` before Spring's default `4xx` mapping and reports an internal error. The existing integration test `GET /members?chatId=invalid-uuid` expects `400`.

## What Changes

- Add an explicit `MethodArgumentTypeMismatchException` handler returning `400` with a `ProblemDetail` body.
- Apply the same fix to all three duplicated advices: `chats`, `users`, `messages`.
- Record the expected status for malformed typed request parameters in the REST API spec.
- No endpoint, parameter, or response schema changes; valid requests behave exactly as before.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `system/rest-api`: error handling now defines `400 Bad Request` with `ProblemDetail` for a request parameter, path variable, or header that cannot be converted to its declared type.

## Impact

- `chats/src/main/kotlin/io/averkhoglyad/shortcut/chat/api/handler/EndpointExceptionAdvice.kt`
- `users/src/main/kotlin/io/averkhoglyad/shortcut/users/api/handler/EndpointExceptionAdvice.kt`
- `messages/src/main/kotlin/io/averkhoglyad/shortcut/message/api/handler/EndpointExceptionAdvice.kt`
- Covered endpoints: `GET /members`, `GET /chats`, `GET /chats/{id}`, `GET /users/{id}`, `PUT /users/{id}`, `GET /messages`, `POST /messages`.
- Existing test `chats/src/test/kotlin/io/averkhoglyad/shortcut/chat/it/ChatMembersEndpointTest.kt` (`returns 400 for invalid chatId format`) starts passing; no test changes required.
- Out of scope: other Spring `4xx` exceptions still caught by the `Throwable` handler (`MissingServletRequestParameterException`, `HttpRequestMethodNotSupportedException`, and similar).
