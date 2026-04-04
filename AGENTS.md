# MOSA Agent Guide

## 1. Operating Rules (Highest Priority)
- Reply in the same language as the user.
- Never create a commit unless the user explicitly asks for one.
- Do not use deprecated APIs.
- Do not use reflection.
- Keep changes simple. Avoid over-engineering, premature optimization, and speculative future-proofing.
- Do not add unreachable defensive branches that cannot be tested through public interfaces.
- If you add a new dependency, use the latest stable version and verify it via web search.
- Keep this file updated when architecture or code structure changes significantly.
- If something generally important for future work should not be forgotten, add it to this `AGENTS.md`, but keep this file clean, short and simple and don't overload it with details.

## 2. Delivery Workflow
### 2.1 Mandatory sequence for business logic (TDD)
1. Plan one small increment.
2. Write one minimal failing unit test.
3. Write the minimal production code to make it pass.
4. Refactor production code without changing test behavior.
5. Refactor test code without changing production behavior.
6. Repeat.

### 2.2 Mandatory sequence for bug fixes
1. Try to reproduce the bug first with an automated test through public behavior.
2. Make the test fail first (red) to confirm reproduction.
3. Implement the minimal production fix.
4. Verify the same test passes after the fix (green).
5. Refactor without changing behavior if needed.

### 2.3 Final quality gate
- Run `./gradlew check` before finishing if the change includes production or test code changes.
- For documentation-only or other non-code changes, `./gradlew check` is not required.

## 3. Code Design Rules
- Formatting/linting: ktlint via `org.jmailen.kotlinter`.
- Use expressive naming instead of comments. Do not write code comments.
- Keep one top-level class per file.
- Prefer concrete types over `dynamic`.
- Prefer `suspend` functions over promises.
- Minimize nullable types where reasonable.
- Keep package naming aligned with `straightway.mosa.*`.
- Keep file names aligned with top-level declarations.
- Use solely English language within code.
- Prefer explicit comparison operators such as `<`, `<=`, avoid `>` and `>=`.
- Apply Clean Code principles pragmatically.
- Apply SOLID principles pragmatically.
- Keep Kotlin source files at max 300 lines (exceptions are permitted with a written justification).
- Keep function argument lists at max 3 arguments + optionally one lambda argument (exceptions are permitted with a written justification).
- Keep function or property bodies at max 20 lines of code (exceptions are permitted with a written justification).
- Write code that is: 
  - simple
  - thoroughly designed
  - easily maintainable
  - intuitively understandable
- As the software is currently not installed in the field, we ignore data migration issues when data schemas change. Instead, the test instances currently running mosa will be required to delete and re-build their data. This is currently OK.

## 4. UI vs Business Logic
- Keep UI-specific code as small as possible and clearly separated from non-UI logic.
- UI code only needs to compile; do not add dedicated tests for UI code.
- Business logic must be test-driven and covered by unit tests.

## 5. Testing & Coverage
- Prefer fast, deterministic tests.
- Use MockK for collaborators.
- Use Ktor test host for endpoint tests.
- In Kotlin/JS tests, use `runTest`.
- Keep each test focused on exactly one minimal scenario.
- Prevent false-positive tests caused by unmet preconditions.
- Test names must use Kotlin backticks, e.g. `fun \`invokes connect handler when interaction finishes\`()`.
- Functionally neutral production changes to improve testability are allowed.
- Target full branch coverage (ideally 100%), but interpret coverage carefully:
  - Kotlin data classes and `@Serializable` code may report inaccurately.
  - Kotlin/JS coverage is currently not measured.
  - Verify the coverage if possible. If not possible, double check manually, if you have tested all branches.

## 6. Build and Verification Commands
- Full quality gate: `./gradlew check`
- Build all: `./gradlew build`
- Run all tests: `./gradlew test`
- Run module tests: `./gradlew :server:test` / `./gradlew :client:test`
- Run single test: `./gradlew :server:test --tests straightway.mosa.SomeTest`
- Coverage run: `./gradlew -Pkover clean test koverXmlReport koverVerify --no-daemon --stacktrace --continue`
- Format all: `./gradlew formatKotlin`
- Lint all: `./gradlew lintKotlin`
- Run server: `./gradlew :server:run`

### Notes
- Coverage report path: `build/reports/kover/report.xml`
- `straightway.mosa.content.QualityTest` may be excluded for intermediate runs unless embedding/vector-store behavior is changed.
- Use `--warning-mode all` when a task requires warning verification.

## 7. Repository Topology
- `server/src/main/kotlin`: Ktor server and business services.
- `client/src/jsMain/kotlin`: Kotlin/JS web client.
- `shared/src/commonMain/kotlin`: Shared DTOs/models.
- `tasks/`: File-based task tracking. See `tasks/AGENTS.md` for the workflow and conventions.
- `models/`: Optional model assets (avoid committing large artifacts unintentionally).
- `mosa_lucene_index/`: Local Lucene index data (delete to reset local state).

## 8. Runtime Snapshot (Compact)
- Server: Ktor + Koin, auth via MIS-JWT bearer tokens.
- DB storage file is fixed to `mosa_db.sqlite` in the server working directory.
- Structured-state SQLite access is split into a reusable `StructuredStateContext` plus `StructuredStateDatabase.inTransaction { ... }`.
  Coordinated multi-store SQLite operations should create transaction-bound store instances via Koin factories instead of passing raw Exposed transactions through service APIs.
- Embeddings/vector storage: ONNX local embeddings + Lucene local vector store.
- Snapshot workflow: freeze export and restore application services live in `server/src/main/kotlin/straightway/mosa/content/snapshot`.
- Client assets are copied into server static resources for packaging.
- Legal document loading is split across dedicated helpers in `server/src/main/kotlin/straightway/mosa/legal`:
  `LegalProfileSource` handles YAML/resource loading, `LegalChainValidator` validates full chains,
  `LegalOperatorProfileTemplateWriter` creates operator templates, and `LegalDocumentFactory` builds canonical/fallback documents.
- Those legal helpers are wired as separate Koin beans.
- Shared ClearTerms code keeps its public API in `shared/src/commonMain/kotlin/straightway/mosa/legal/clearterms`
  and groups implementation details into subpackages: `canonical`, `locale`, `parse`, `placeholder`, `render`, `validation`.
- Classpath/resource access in the legal module goes through `ResourceTextReader`, `ResourceDirectoryEntriesProvider`, and `ResourceUrlsProvider`.
- ClearTerms YAML loading supports optional language fragment files:
  - Baseline: `<name>.<lang>.yaml` fragments contribute to root `texts`.
  - Profile: `<name>.<lang>.yaml` fragments contribute to `overrides.texts`.

## 9. Security Constraints
- Ed25519 signing uses transient keys unless explicitly persisted.
- Certificates are URL-safe Base64 encoded and time-limited.
- Never commit secrets or credential material.
- Never commit large model artifacts unintentionally.

## 10. Remember
- Under JDK 25, `sun.misc.Unsafe` warnings still remain in upstream tooling even after upgrading project Kotlin and pruning extra test dependencies:
  - `org.jmailen.kotlinter` currently pulls `ktlint 1.8.0`, which still depends on `kotlin-compiler-embeddable 2.2.21`.
  - Server tests still trigger a Byte Buddy `Unsafe` warning through MockK even on `byte-buddy 1.18.7`.
- A full removal of those warnings likely requires future upstream releases or replacing the affected linting/mocking stack, not just local version bumps.
