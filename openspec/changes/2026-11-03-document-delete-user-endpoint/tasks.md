# Tasks

## 1. Spec delta

- [ ] 1.1 Add `DELETE /users/{id}` row to the Users REST endpoints table in the delta spec with response `204 No Content`. Add scenario "Delete user" (204 on success) and scenario "Delete unknown user" (404 ProblemDetail). Verify `openspec validate --change document-delete-user-endpoint --strict` passes.
- [ ] 1.2 Sync the delta to `openspec/specs/system/rest-api/spec.md`. Verify `openspec validate --specs` passes.

## 2. Archive

- [ ] 2.1 Archive the change after sync (`openspec/changes/archive/2026-11-03-document-delete-user-endpoint`).