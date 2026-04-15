# Poor Man's Task Board

For code or documentation work, also follow the root [`AGENTS.md`](/home/kai/Projects/straightway/mosa/AGENTS.md) and the local skills referenced there. This file governs the filesystem-based task board. Skill coordination inside a task directory is defined in [`tasks/Coordination.md`](/home/kai/Projects/straightway/mosa/tasks/Coordination.md).

This directory is an intentionally simple task board in the filesystem. A task is a folder. Its status is defined only by its parent status folder.

## Status Folders

- `1-open`: known, not started yet
- `2-in-progres`: actively in progress
- `3-done`: functionally completed
- `4-discarded`: intentionally rejected
- `5-follow-up`: finished for now or paused, revisit later

Each task always has exactly one status. Status changes happen by moving the whole task folder.

## Task Format

Schema:

```text
tasks/<status>/<NNN>-<ShortName>/
```

Example:

```text
tasks/1-open/002-LegalDocumentChangeConfirmationFlow/
```

Required file:

- `Description.md`: problem, goal, important constraints

Allowed:

- additional Markdown files for context
- additional files or simple substructures when they help execution
- coordination files as defined in [`tasks/Coordination.md`](/home/kai/Projects/straightway/mosa/tasks/Coordination.md)

Markdown is the default. Content may stay concise, but it must be executable without ambiguity for a human or coding agent.

Task-specific docs belong in the task folder. Project-wide docs belong in `spec/`. Help texts and serveable docs belong under repository resources, including `server/src/main/resources/doc/serve` and `server/src/main/resources/clearterms`. Use full-project scans only as a last resort.
- Use this Bash script to find the current task:

```bash
tasks=$(find tasks/2-in-progres -mindepth 1 -maxdepth 1 -type d | sort)
[ "$(printf '%s\n' "$tasks" | sed '/^$/d' | wc -l)" -eq 1 ] || exit 1
current_task=$(printf '%s\n' "$tasks" | sed '/^$/d')
```

- Then inspect only files inside `$current_task` unless the coordinating skill explicitly allows a wider scope.

## Subtasks

If a task is split up, subtasks live as Markdown files in the same task folder.
Before substantial implementation starts, the task should be checked for implementation readiness.
If the scope or structure requires decomposition, the architect should split the task into subtasks before implementation proceeds.

Schema:

```text
subNNN-ShortDescription.md
```

Example:

```text
sub001-CreateMissingOperatorProfileFiles.md
```

Rules:

- `NNN` is a three-digit running number within the task.
- `ShortDescription` stays short, stable, and ASCII-compatible.
- Each subtask should be independently executable.
- The current subtask is the open subtask with the lowest `NNN`.
- Implementation, testing, and quality work focus on the current subtask only.
- Completed subtasks are not moved, but renamed:

```text
done-subNNN-ShortDescription.md
```

Example:

```text
done-sub001-CreateMissingOperatorProfileFiles.md
```

- The main task remains the functional umbrella. Subtasks extend `Description.md`; they do not replace it.
- Only quality work closes a subtask by renaming it from `subNNN-...md` to `done-subNNN-...md`.

### Required Content Of The Last Subtask

If a task is split into subtasks, the last subtask must contain:

1. Final test of the integrated overall change
2. Review and, if needed, update of documentation in:
   - `spec`
   - `server/src/main/resources/clearterms`
   - `server/src/main/resources/doc/serve`
3. Review of new third-party licenses for compatibility with `LICENSE`

The final test must clearly state:

- which components are tested together
- which preconditions apply
- which steps must be executed
- how success or failure is recognized
- whether the test is fully automated, manually executable inside the repo by an agent, manually executable at runtime, or only executable by a human

If a human must run the test, the description must be executable without follow-up questions.

## Numbering

- New tasks get the next free three-digit number.
- The number stays unchanged for the full lifecycle.
- `ShortName` stays concise, readable, and stable.

## Workflow

1. New task: create a folder in `1-open`, then write `Description.md`.
2. Start work: move the folder to `2-in-progres`.
3. Before substantial implementation, confirm whether the task needs subtask decomposition.
   - When finding the current task, inspect only `tasks/2-in-progres/` and its immediate children.
   - Do not recurse into deeper task folders or scan the full `tasks/` tree.
   - Use exactly this discovery command: `find tasks/2-in-progres -mindepth 1 -maxdepth 1 -type d | sort`
   - For architecture files, use exactly this command inside the selected task folder: `find tasks/2-in-progres/<current-task> -maxdepth 1 -type f \( -name 'ArchitectureReview.md' -o -name 'ArchitectureNotes.md' -o -name 'Architecture*.md' \) | sort`
4. If decomposition is needed, the architect creates or updates the subtasks before implementation continues.
5. Developer, tester, and quality work on the current subtask until quality is satisfied.
6. Quality closes the current subtask by renaming it to `done-subNNN-...md`.
7. If that was the last open subtask, quality performs a final whole-task review before the task is proposed for completion.
8. Finish work: move only after human approval to `3-done` or `5-follow-up`.
9. Discard task: move only after human approval to `4-discarded`.
10. Revisit later: move to `5-follow-up`.

If the task scope changes substantially:

- update `Description.md`
- if it becomes a real split, create a new task with a new number

## Conventions

- Descriptions should default to English.
- Folder names are ASCII and contain no spaces.
- No priority logic in the name.
- Completed tasks are not renumbered.
- `4-discarded` is a terminal status, not a parking lot.
- `5-follow-up` is for deliberate later resumption.
- The spelling `2-in-progres` remains intentionally unchanged.
