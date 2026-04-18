---
name: mosa-quality
description: >
  Quality gate skill for this repository. Use when assessing whether a change is ready to ship
  or review: tests, coverage, documentation, build verification, and evidence that the change
  is complete rather than merely implemented. Use this skill near the end of a change or during
  review.
---

# Mission

Judge delivery completeness, not only correctness.
Require missing documentation updates to be fixed, but do not treat documentation changes as quality-owned implementation work.
If the user does not name a different task, continue quality-checking the active task or current subtask from the previous turn.

Questions to answer:

- Is the change covered by useful tests?
- Are important branches still unproven?
- Does repository documentation still match behavior?
- Were required checks run?
- Is any supporting evidence still missing for review or release?

# Task Coordination

Follow the shared task-coordination rules in [`tasks/Coordination.md`](../../../tasks/Coordination.md).
Write quality findings to `QualityGate.md`.
Evaluate the current subtask until it is ready to close.
Close a satisfied subtask by renaming it from `subNNN-...md` to `done-subNNN-...md`.
If that was the last open subtask, perform a final whole-task review.

Task discovery must be narrow:

```bash
tasks=$(find tasks/2-in-progres -mindepth 1 -maxdepth 1 -type d | sort)
[ "$(printf '%s\n' "$tasks" | sed '/^$/d' | wc -l)" -eq 1 ] || exit 1
current_task=$(printf '%s\n' "$tasks" | sed '/^$/d')
```

After that, inspect only files inside `$current_task`.
Do not run `find tasks ...` with broad name patterns such as `done-sub*.md`, `sub*.md`, `AGENTS.md`, or [`tasks/Coordination.md`](../../../tasks/Coordination.md).
Do not use recursive repository scans to discover task files.

# Verification

- If production or test code changed, verify that the developer documented a final `./gradlew check` run in `ImplementationNotes.md`.
- For documentation-only or similar non-code changes, `./gradlew check` is not required.
- Use `./gradlew formatKotlin` and `./gradlew lintKotlin` when formatting or lint issues are relevant.
- Coverage command when needed: `./gradlew -Pkover clean test koverXmlReport koverVerify --no-daemon --stacktrace --continue`
- Coverage report path: `build/reports/kover/report.xml`
- `straightway.mosa.content.QualityTest` may be skipped in intermediate runs unless embedding or vector-store behavior changed.
- Use `--warning-mode all` when warning verification matters.

# Completion Checklist

Check whether the change also requires updates to:

- `spec/`
- `server/src/main/resources/clearterms`
- `server/src/main/resources/doc/serve`
- license or dependency documentation when new third-party code is added

Call out gaps explicitly if they are intentionally deferred.
Record those gaps in `QualityGate.md`.

# Coverage Caveats

- Coverage on data classes and `@Serializable` code can be misleading.
- Kotlin/JS coverage is not measured.
- Absence of a coverage number is not proof that behavior is untested; inspect the actual test surface.
