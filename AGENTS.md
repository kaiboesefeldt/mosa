# MOSA Agent Guide

Keep this file short. Only stable, repo-wide rules belong here.

## Global Rules

- Reply in user language.
- Never create a commit unless the user explicitly asks.
- Do not use deprecated APIs.
- Do not use reflection.
- New dependencies must use the latest stable version and be verified via web search.

## Repo Map

- `server/src/main/kotlin`: Ktor server and business services
- `client/src/jsMain/kotlin`: Kotlin/JS web client
- `shared/src/commonMain/kotlin`: shared DTOs and models
- `tasks/`: file-based task tracking, see `tasks/AGENTS.md`
- `models/`: optional model assets, avoid committing large artifacts
- `mosa_lucene_index/`: local Lucene index data, delete to reset local state

## Document Scope

- `AGENTS.md` files may exist in multiple directories.
- Task coordination documents belong in the current task folder.
- Project documentation belongs in `spec/` unless it is task-specific.
- `spec/mvp.md` is the launch-scope baseline for public-release decisions.
- Help texts and serveable reference docs belong under repository resources, including `server/src/main/resources/doc/serve` and `server/src/main/resources/clearterms`.
- Complete repository-wide search is a last resort only.
- Prefer the narrowest path that can contain the document before scanning the whole project.
- Use this Bash script to find the current task:

```bash
tasks=$(find tasks/2-in-progres -mindepth 1 -maxdepth 1 -type d | sort)
[ "$(printf '%s\n' "$tasks" | sed '/^$/d' | wc -l)" -eq 1 ] || exit 1
current_task=$(printf '%s\n' "$tasks" | sed '/^$/d')
```

- Do not replace that script with a broader search.

## Architecture And Security

- Top-level architecture lives in `spec/Architecture.md`.
- Launch scope lives in `spec/mvp.md`.
- Ed25519 signing uses transient keys unless explicitly persisted.
- Certificates are URL-safe Base64 encoded and time-limited.
- Never commit secrets, credential material, or large model artifacts unintentionally.

## Local Skills

- `.agents/skills/mosa-developer`: implementation workflow, TDD loop, Kotlin code-shape rules
- `.agents/skills/mosa-tester`: adversarial test design and robustness checks
- `.agents/skills/mosa-quality`: coverage, docs, verification, review readiness
- `.agents/skills/mosa-developer-reviewer`: critical code review for bugs, regressions, and missing evidence
- `.agents/skills/mosa-architect`: structural boundaries and architecture changes
- `.agents/skills/mosa-architecture-reviewer`: critical architecture review for structural risks and design gaps

The listed `mosa-*` work skills continue the active task by default unless the user says otherwise. New skills must opt in to that behavior explicitly.

## Known Tooling Noise

- Under JDK 25, `sun.misc.Unsafe` warnings still come from upstream tooling.
- `org.jmailen.kotlinter` currently pulls `ktlint 1.8.0`, which still depends on `kotlin-compiler-embeddable 2.2.21`.
- Server tests still trigger Byte Buddy `Unsafe` warning through MockK even with `byte-buddy 1.18.7`.
- Full removal likely needs upstream changes or replacing the affected tooling.
