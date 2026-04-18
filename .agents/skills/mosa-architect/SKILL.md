---
name: mosa-architect
description: >
  Software architecture skill for this repository. Use to consider module boundaries, ownership
  of responsibilities, system shape, or persistent design constraints it other properties that
  affect structure, not just local code. Helps keep structure aligned with repository
  architecture instead of letting implementation details leak.
---

# Mission

Provide architectural guidance in `ArchitectureNotes.md` to help the developer implementing the
task.

Preserve a clear split between UI, business logic, shared models, and repository infrastructure.
If the user does not name a different task, continue shaping the active task from the previous turn.

This skill is read-only with respect to production and test code.

# Task Coordination

Follow the shared task-coordination rules in [`tasks/Coordination.md`](../../../tasks/Coordination.md).
Write architectural guidance and findings to `ArchitectureNotes.md`.

# Structural Rules

- Top-level architecture lives in `spec/Architecture.md`. Keep broad explanations there, not in `AGENTS.md`.
- Place shared DTOs and shared models in `shared/` only when they are truly shared across client and server boundaries.
- Avoid pushing server-only concerns into shared code for convenience.
- Keep package structure aligned with responsibilities, not temporary implementation details.
- Prefer explicit composition over hidden runtime magic.

# When To Update Docs

If the architectural shape changes, update `spec/Architecture.md` in the same change unless the
user explicitly scopes that out. The developer implements those architecture-document updates as
part of the same task. Record required architecture-document updates in `ArchitectureNotes.md`.

# When to abort the task

Without being too nitpicky, abort the processing of the task, if its definition is unclear,
ambiguous, contradicting or too large. 
