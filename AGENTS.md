# MOSA Agent Guide

## 1. Highest-Priority Rules
- Reply in user language.
- Never create commit unless user explicitly asks.
- Do not use deprecated APIs.
- Do not use reflection.
- Keep changes simple. Avoid over-engineering, premature optimization, speculative future-proofing.
- Do not add defensive branches that cannot be reached and tested through public behavior.
- New dependencies: use latest stable version and verify via web search.
- Keep this file short. Only stable, repo-wide rules belong here.

## 2. Required Delivery Workflow

### Business Logic: mandatory TDD loop
1. Plan one small increment.
2. Write one minimal failing unit test.
3. Write minimal production code to pass.
4. Refactor production code without changing behavior.
5. Refactor test code without changing behavior.
6. Repeat.

### Bug Fixes: mandatory flow
1. Reproduce bug with automated test through public behavior.
2. Confirm test fails first.
3. Implement minimal fix.
4. Confirm same test passes.
5. Refactor if useful without changing behavior.

### Final quality gate
- If production or test code changed, run `./gradlew check` before finishing.
- For documentation-only or other non-code changes, `./gradlew check` is not required.

## 3. Design Rules
- Formatting/linting: ktlint via `org.jmailen.kotlinter`.
- Use expressive naming. Do not write code comments.
- One top-level class per file.
- Prefer concrete types over `dynamic`.
- Prefer `suspend` functions over promises.
- Minimize nullable types where reasonable.
- Keep package names aligned with `straightway.mosa.*`.
- Keep file names aligned with top-level declarations.
- Use English only inside code.
- Prefer `<` and `<=`; avoid `>` and `>=` when equivalent expression is possible.
- Apply Clean Code and SOLID pragmatically.
- Kotlin source files: max 300 lines unless justified.
- Function argument lists: max 3 arguments plus optional lambda unless justified.
- Function and property bodies: max 20 lines unless justified.
- Data migrations are currently ignored. If schema changes, test instances may rebuild data from scratch.

## 4. UI vs Business Logic
- Keep UI code small and clearly separated from non-UI logic.
- UI code only needs to compile; do not add dedicated UI tests.
- Business logic must be test-driven and covered by unit tests.

## 5. Testing Rules
- Prefer fast, deterministic tests.
- Use MockK for collaborators.
- Use Ktor test host for endpoint tests.
- In Kotlin/JS tests, use `runTest`.
- Each test covers exactly one minimal scenario.
- Guard against false positives caused by unmet preconditions.
- Test names use Kotlin backticks, for example `fun \`invokes connect handler when interaction finishes\`()`.
- Functionally neutral production changes for testability are allowed.
- Target full branch coverage where practical.
- Coverage caveats:
  - data classes and `@Serializable` coverage may be misleading
  - Kotlin/JS coverage is not measured
  - if automated coverage verification is not possible, verify branches manually

## 6. Build and Verification Commands
- Full quality gate: `./gradlew check`
- Build all: `./gradlew build`
- Run all tests: `./gradlew test`
- Server tests: `./gradlew :server:test`
- Client tests: `./gradlew :client:test`
- Single server test: `./gradlew :server:test --tests straightway.mosa.SomeTest`
- Coverage: `./gradlew -Pkover clean test koverXmlReport koverVerify --no-daemon --stacktrace --continue`
- Format: `./gradlew formatKotlin`
- Lint: `./gradlew lintKotlin`
- Run server: `./gradlew :server:run`

Notes:
- Coverage report: `build/reports/kover/report.xml`
- `straightway.mosa.content.QualityTest` may be skipped in intermediate runs unless embedding or vector-store behavior changed.
- Use `--warning-mode all` when warning verification matters.

## 7. Repo Map
- `server/src/main/kotlin`: Ktor server and business services
- `client/src/jsMain/kotlin`: Kotlin/JS web client
- `shared/src/commonMain/kotlin`: shared DTOs and models
- `tasks/`: file-based task tracking, see `tasks/AGENTS.md`
- `models/`: optional model assets, avoid committing large artifacts
- `mosa_lucene_index/`: local Lucene index data, delete to reset local state

## 8. Architecture and Security
- Top-level architecture lives in `spec/Architecture.md`.
- Keep broad structural explanations there, not here.
- Ed25519 signing uses transient keys unless explicitly persisted.
- Certificates are URL-safe Base64 encoded and time-limited.
- Never commit secrets, credential material, or large model artifacts unintentionally.

## 9. Known Tooling Noise
- Under JDK 25, `sun.misc.Unsafe` warnings still come from upstream tooling.
- `org.jmailen.kotlinter` currently pulls `ktlint 1.8.0`, which still depends on `kotlin-compiler-embeddable 2.2.21`.
- Server tests still trigger Byte Buddy `Unsafe` warning through MockK even with `byte-buddy 1.18.7`.
- Full removal likely needs upstream changes or replacing the affected tooling.
