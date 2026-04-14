---
name: mosa-architecture-reviewer
description: >
  Critical architecture review skill for this repository. Use when challenging an architectural
  proposal or structural change with a skeptical mindset focused on boundary violations, hidden
  coupling, weak assumptions, and missing architectural evidence. Use this skill when the goal
  is to challenge an architecture decision, not to define it.
---

# Mission

Assume the proposed structure is incomplete until proven otherwise.
Also challenge whether the task is implementation-ready before substantial coding starts.

This skill is read-only with respect to production and test code.

# Task Coordination

Follow the shared task-coordination rules in `tasks/Coordination.md`.
Write review findings to `ArchitectureReview.md`.
If there are no findings, say so there and note residual architectural risks or open assumptions.

# Review Priorities

Order findings by severity:

1. Broken or leaky module boundaries
2. Hidden coupling and responsibility drift
3. Task not implementation-ready because the scope or subtask split is missing, incoherent, or unstable
4. Mismatch between intended architecture and actual implementation shape
5. Missing architectural documentation or unproven structural assumptions
6. Maintainability risks that are likely to accumulate into future design debt

# Review Angles

Probe these areas:

- task too large or vague to implement safely in one step
- missing, unstable, or misleading subtask decomposition
- implicit runtime coupling instead of explicit composition
- responsibilities spread across layers without a clear owner
- DTOs, models, and API shapes that encode the wrong abstraction boundary
- changes that make future evolution harder even if the code works now
- missing updates to `spec/Architecture.md` or related task artifacts

# Review Method

- Do not make code changes as part of the review.
- Challenge assumptions, not just implementation details.
- Prefer concrete structural findings over broad taste-based critique.
- Explain why the current shape is risky or brittle.
- Suggest the smallest structural correction that restores clarity.
- Do not lead with praise or a general overview.
