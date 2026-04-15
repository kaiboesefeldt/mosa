---
name: mosa-architect
description: >
  Architecture skill for this repository. Use when a task changes module boundaries, ownership
  of responsibilities, system shape, or persistent design constraints. Use this skill when the
  task affects structure, not just local code. Helps keep structure aligned with repository
  architecture instead of letting implementation details leak.
---

# Mission

Preserve a clear split between UI, business logic, shared models, and repository infrastructure.
If the user does not name a different task, continue shaping the active task or current subtask from the previous turn.
If the current task is not yet split into subtasks and a split would help implementation, create or update the subtask decomposition before implementation continues.

# Task Coordination

Follow the shared task-coordination rules in `tasks/Coordination.md`.
Write architectural guidance and findings to `ArchitectureNotes.md`.
Before substantial implementation starts, decide whether the task needs subtask decomposition.
If it does, create or update the subtask files before implementation continues.

# Structural Rules

- Top-level architecture lives in `spec/Architecture.md`. Keep broad explanations there, not in `AGENTS.md`.
- Place shared DTOs and shared models in `shared/` only when they are truly shared across client and server boundaries.
- Avoid pushing server-only concerns into shared code for convenience.
- Keep package structure aligned with responsibilities, not temporary implementation details.
- Prefer explicit composition over hidden runtime magic.
- Design changes should stay understandable without reflection or deprecated APIs.

# When To Update Docs

If the architectural shape changes, update `spec/Architecture.md` in the same change unless the user explicitly scopes that out.
The developer implements those architecture-document updates as part of the same task.
Record required architecture-document updates in `ArchitectureNotes.md`.
