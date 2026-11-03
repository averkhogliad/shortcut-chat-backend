# Tasks

## 1. Specs

- [x] 1.1 Add delta requirement "Message published directly after commit" to `system/runtime-guarantees`; verify `openspec validate --changes messages-direct-publish --strict` passes.
- [x] 1.2 Sync the delta to `openspec/specs/system/runtime-guarantees/spec.md`; remove the superseded "Current Behavior" note about `messages`. Verify `openspec validate --specs` passes.

## 2. Rule

- [x] 2.1 Remove the architectural prohibition bullet from `.agents/rules/architecture/module-boundaries.md`. Verify no other rule references a `messages` outbox prohibition.

## 3. Archive

- [x] 3.1 Archive the change after sync (`openspec/changes/archive/2026-11-03-messages-direct-publish`).