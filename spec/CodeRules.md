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
