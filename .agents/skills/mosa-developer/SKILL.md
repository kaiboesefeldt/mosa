---
name: mosa-developer
description: >
  Developer workflow for this repository. Use when implementing or refactoring code, especially
  for business logic, API changes, and production code touched by tests. Covers the mandatory
  TDD loop, code-shape rules, and repository-specific Kotlin conventions. Use this skill when
  writing or changing production code.
---

# Mission

Deliver the smallest correct change, driven by tests, while keeping the codebase simple and structurally clear.
When implementation changes require documentation updates, make those documentation changes in the same task.
If the user does not name a different task, continue working on the active task or current subtask from the previous turn.

# Workflow

For business logic, stay in a small TDD loop:

1. Pick one minimal behavior increment.
2. Add one failing automated test through public behavior.
3. Write the smallest production change that makes it pass.
4. Refactor production code without changing behavior if it improves clarity.
5. Refactor the test without changing behavior if it improves clarity.
6. Repeat until the task is done.

For bug fixes:

1. Reproduce the bug with an automated test through public behavior.
2. Confirm that test fails first.
3. Implement the minimal fix.
4. Confirm the same test passes.
5. Refactor if it improves clarity.

# Task Coordination

Follow the shared task-coordination rules in `tasks/Coordination.md`.
Maintain `ImplementationNotes.md`.
When the task has subtasks, work only on the current subtask.
Address findings in `ArchitectureNotes.md`, `ArchitectureReview.md`, `CodeReview.md`, `TestFindings.md`, and `QualityGate.md` explicitly instead of silently ignoring them.
Update the relevant `status` and `Developer Response` fields when implementing, rejecting, deferring, or superseding a finding.
When `TestFindings.md` documents a failing test or a meaningful uncovered gap, treat that as a handoff back from the tester and resolve it before asking quality to close the subtask.
Run `./gradlew check` once near the end when production or test code changed, and document the command and result in `ImplementationNotes.md`.

# Code Rules

- Keep changes simple. No over-engineering, speculative abstraction, or premature optimization.
- Do not add defensive branches that cannot be reached and tested through public behavior.
- Use expressive naming.
- Do not write code comments unless the user explicitly asks.
- One top-level class per file.
- Prefer concrete types over `dynamic`.
- Prefer `suspend` functions over promises.
- Minimize nullable types where reasonable.
- Keep package names aligned with `straightway.mosa.*`.
- Keep file names aligned with top-level declarations.
- Use English only inside code.
- Prefer `<` and `<=`; avoid `>` and `>=` when an equivalent expression is clear.
- Apply Clean Code and SOLID pragmatically, never ceremonially.
- Kotlin source files should stay within 300 lines unless there is a clear reason not to.
- Keep function argument lists within 3 arguments plus an optional lambda unless there is a clear reason not to.
- Keep function and property bodies within 20 lines unless clarity would suffer.
- Functionally neutral production changes for testability are allowed.
- Data migrations are currently ignored. If the schema changes, test instances may rebuild from scratch.

# UI Boundary

- Keep UI code small and clearly separated from non-UI logic.
- UI code only needs to compile. Do not add dedicated UI tests unless the user asks.
- Business logic belongs outside the UI layer and must be covered by tests.

# Coordination

- Pull architecture rules from `mosa-architect` when the change affects boundaries, module responsibilities, or long-lived structure.
- Pull `mosa-architecture-reviewer` when you want to challenge a proposed structure or recent structural change.
- Pull `mosa-tester` when you need a stronger adversarial test design pass.
- Pull `mosa-quality` before finalizing broad or risky changes that may require documentation or coverage follow-up.
- Pull `mosa-developer-reviewer` when you want a critical post-implementation pass.
- Update user-facing or task-relevant documentation in the same change when the implementation requires it.
