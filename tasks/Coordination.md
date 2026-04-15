# Task Coordination

This file defines how repository-specific skills coordinate within a task directory.

## Current Task Directory

By default, the current task directory is the only task folder directly under `tasks/2-in-progres/`.

If the user explicitly points to a different task, use that task instead.
If there are multiple folders under `tasks/2-in-progres/` and the user did not specify one, the current task is ambiguous and must be clarified before writing task coordination files.

When locating the current task, inspect only the immediate children of `tasks/2-in-progres/`; do not recurse into nested task folders.

## Coordination Files

These files are the default coordination surface inside the current task directory:

- `ImplementationNotes.md`: developer-owned plan, current increment, implementation status, tests run, unresolved items
- `ArchitectureNotes.md`: architect-owned structural constraints, required boundaries, documentation impact, open questions
- `ArchitectureReview.md`: architecture-reviewer-owned challenges, structural risks, and design gaps
- `CodeReview.md`: developer-reviewer-owned code review findings
- `TestFindings.md`: tester-owned adversarial findings and missing-test recommendations
- `QualityGate.md`: quality-owned release-readiness findings, documentation gaps, verification status

Skills may read any of these files when they exist and when the contents are relevant to the current task.

## Readiness

Before substantial implementation starts, the task should be checked for implementation readiness.

- The architect decides whether the task needs subtask decomposition.
- If decomposition is needed, the architect creates or updates the subtasks before implementation continues.
- The architecture reviewer challenges whether the task is implementation-ready, including whether the current subtask split is sufficient and coherent.
- The developer should not silently work around a missing or broken task split; instead, the need for restructuring should be raised through the coordination files.

## Current Subtask

When a task has subtasks, the current subtask is the open subtask with the lowest `NNN`.

- The developer works on the current subtask only.
- The tester tests the current subtask only.
- Quality evaluates the current subtask only until it is ready to close.
- Only quality closes a subtask by renaming `subNNN-...md` to `done-subNNN-...md`.
- If the closed subtask was the last open subtask, quality then performs a final whole-task review.

## Tester Handoff

The tester works adversarially against the current subtask.

- The tester may explore by adding or tightening tests and running targeted test commands.
- The tester should aim to expose a real problem or missing coverage with a failing test.
- If an exploratory test addition or tightening does not lead to a failing test that proves a meaningful gap, the tester removes or reverts that test change before handing off.
- The tester does not leave behind green-only confirmation tests as a handoff artifact.
- The tester documents the targeted test run and the result in `TestFindings.md`.
- Once a failing test or meaningful gap is documented, the task returns to the developer.

## Findings Format

When a coordination file contains findings, use stable IDs per file, for example `CR-1`, `TEST-2`, `ARCH-1`, `AR-1`, `QA-3`.

Each finding should include at least:

- title
- status
- evidence or rationale
- recommended action
- `Developer Response`

The developer updates the `status` and `Developer Response` fields when a finding is implemented, rejected with rationale, deferred, or superseded.

## Expensive Verification

`./gradlew check` is treated as an expensive final verification step.

- The developer runs it once near the end of the task, not once per subtask.
- The developer records the command, outcome, and any relevant notes in `ImplementationNotes.md`.
- Quality checks that this documentation exists and is plausible; quality does not rerun `./gradlew check` by default.
