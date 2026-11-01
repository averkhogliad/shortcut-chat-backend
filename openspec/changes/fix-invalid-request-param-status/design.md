# Design

## Context

See `proposal.md`. Each MVC service has its own copy of `EndpointExceptionAdvice`. All three copies end with `@ExceptionHandler(Throwable::class)`, registered in `ExceptionHandlerExceptionResolver`, which runs before Spring's `DefaultHandlerExceptionResolver`. The catch-all therefore decides the status for every unmapped exception. `MethodArgumentTypeMismatchException` is a `TypeMismatchException`, not an `IllegalArgumentException`, so the existing `IllegalArgumentException` handler does not apply and the catch-all answers `500`.

## Goals / Non-Goals

**Goals:**

- Convert a failed parameter, path-variable, or header type conversion into `400` with `ProblemDetail`.
- Keep identical behavior in `chats`, `users`, and `messages`.

**Non-Goals:**

- No consolidation of the duplicated advice classes.
- No handling of other client errors currently swallowed by the catch-all (`MissingServletRequestParameterException`, `HttpRequestMethodNotSupportedException`, unsupported media type).
- No change to valid-request behavior or response payloads.

## Decisions

- **Handle `MethodArgumentTypeMismatchException`, not `TypeMismatchException`.** The narrower type covers query parameters, path variables, and headers bound by Spring MVC and does not silently change behavior of unrelated conversion failures. Rationale: minimal contract change.
- **Add a dedicated `@ExceptionHandler` instead of extending `ResponseEntityExceptionHandler`.** That class would alter handling of many other exceptions at once, which is outside the reported defect.
- **Return a fixed `ProblemDetail` detail string (`invalid.argument`) instead of the exception message.** Consistent with the existing `invalid.body` handler and avoids exposing internal converter details.
- **Duplicate the handler in all three advices.** Matches the current project structure; consolidation is separate technical debt.

## Risks / Trade-offs

- Duplication means a future fix must touch three files → noted in `proposal.md`; consolidation remains a separate task.
- The catch-all still masks other `4xx` exceptions → out of scope, recorded in the proposal.
- Integration verification needs Docker (Testcontainers) → if unavailable, report it as an environment limit, not a passing check.
