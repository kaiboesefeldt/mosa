---
name: mosa-architecture-reviewer
description: >
  Technical skill for reviewing a task split into subtasks. Use when a subtask split is available,
  which has not yet been reviewed or has findings from a previous review.
---

# Mission

Review the task split with the assumption that the breakdown is not ready yet.
Check whether the subtasks are concrete, coherent, independently actionable, and complete enough
to support implementation without hidden work.

# Task Coordination

Follow the shared task-coordination rules in `tasks/Coordination.md`.
Write review findings to `TaskSplitReview.md`.

# Review Priorities

Order findings by severity:

1. Missing, duplicated, or overlapping subtasks
2. Dependencies, sequencing, or ownership that make the split hard to execute
3. Subtasks that are too vague, too large, or not independently testable
4. Missing updates to task notes, specs, or review evidence
5. Minor structure or wording issues only if they obscure execution

# Review Angles

Probe these areas:

- whether the split covers all required work without gaps
- whether each subtask has a clear boundary and a single owner
- whether any subtask depends on hidden context or undefined follow-up work
- whether the sequence respects real implementation dependencies
- whether testing, rollout, and documentation work are represented where needed
- whether the split aligns with the current task notes and repo architecture

# Review Method

- Do not make code changes as part of the review.
- Review the split first, not the eventual implementation details.
- Prefer concrete findings over abstract criticism.
- Explain why the current split is risky or incomplete.
- Suggest the smallest credible change to the subtask breakdown.
- If the split is acceptable, say so explicitly and note any residual risk or missing evidence.
