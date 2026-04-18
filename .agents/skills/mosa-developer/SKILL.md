---
name: mosa-developer
description: >
  Developer workflow for this repository. Use when implementing or refactoring code. Covers the
  mandatory TDD loop, code-shape rules, and repository-specific Kotlin conventions. Use this skill
  when writing or changing production or test code.
---

# Mission

Deliver the smallest correct change, driven by tests, while keeping the codebase simple and
structurally clear. When implementation changes require documentation updates, make those
documentation changes in the same subtask. If the user does not name a different task, continue
working on the current subtask of the active task from the previous turn.

# Workflow

- For business logic and features, see the [feature workflow](references/FeatureWorkflow.md)
- For bug fixes, see the [feature workflow](references/BugWorkflow.md)

# Task Coordination

Follow the shared task-coordination rules in [`tasks/Coordination.md`](../../../tasks/Coordination.md).
Maintain `ImplementationNotes.md`.
Work only on the current subtask.
Address findings in `ArchitectureNotes.md`, `ArchitectureReview.md`, `CodeReview.md`, `TestFindings.md`, and `QualityGate.md` explicitly instead of silently ignoring them.
Update the relevant `status` and `Developer Response` fields when implementing, rejecting, deferring, or superseding a finding.
When `TestFindings.md` documents a failing test or a meaningful uncovered gap, treat that as a handoff back from the tester and resolve it before asking quality to close the subtask.
If production or test code changed, run `./gradlew check` once near the end, and document the command and result in `ImplementationNotes.md`.

# Code Rules

See [common code rules](../../../spec/CodeRules.md)

# UI Boundary

- Keep UI code small and clearly separated from non-UI logic.
- Pure UI code without business logic only needs to compile. Do not add dedicated UI tests unless the user asks.
- Business logic belongs outside the UI layer and must be covered by tests.
