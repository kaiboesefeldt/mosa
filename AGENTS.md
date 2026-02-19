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
- If something important for future work should not be forgotten, add it to this `AGENTS.md`.

## 2. Delivery Workflow
### 2.1 Mandatory sequence for business logic (TDD)
1. Plan one small increment.
2. Write one minimal failing unit test.
3. Write the minimal production code to make it pass.
4. Refactor production code without changing test behavior.
5. Refactor test code without changing production behavior.
6. Repeat.

### 2.2 Final quality gate
- Always run `./gradlew check` before finishing.

## 3. Code Design Rules
- Kotlin version: 2.2.
- Formatting/linting: ktlint via `org.jmailen.kotlinter`.
- Use expressive naming instead of comments. Do not write code comments.
- Keep one top-level class per file.
- Prefer concrete types over `dynamic`.
- Prefer `suspend` functions over promises.
- Minimize nullable types where reasonable.
- Keep package naming aligned with `straightway.mosa.*`.
- Keep file names aligned with top-level declarations.
- Use English for identifiers and test names.
- Prefer explicit comparison operators such as `<`, `<=`, `>`, `>=`.
- Apply SOLID principles pragmatically.

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
- Target high coverage (ideally 100%), but interpret coverage carefully:
  - Kotlin data classes and `@Serializable` code may report inaccurately.
  - Kotlin/JS coverage is currently not measured.

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
- Run CLI example: `./gradlew :cli:run --args="search --pod http://localhost:8080 --topic 'query'"`

### Notes
- Coverage report path: `build/reports/kover/report.xml`
- `straightway.mosa.content.QualityTest` may be excluded for intermediate runs unless embedding/vector-store behavior is changed.
- Gradle warnings can be ignored.

## 7. Repository Topology
- `server/src/main/kotlin`: Ktor server and business services.
- `client/src/jsMain/kotlin`: Kotlin/JS web client.
- `cli/src/main/kotlin/straightway/mosa/cli`: JVM CLI commands.
- `shared/src/commonMain/kotlin`: Shared DTOs/models.
- `models/`: Optional model assets (avoid committing large artifacts unintentionally).
- `mosa_lucene_index/`: Local Lucene index data (delete to reset local state).

## 8. Runtime Snapshot (Compact)
- Server: Ktor + Koin, auth via MIS-JWT bearer tokens.
- Routes: `/v1/approvegid` is open; `/v1/publish`, `/v1/reply`, `/v1/query`, `/v1/queryon` require auth.
- Embeddings/vector storage: ONNX local embeddings + Lucene local vector store.
- Client assets are copied into server static resources for packaging.

## 9. Security Constraints
- Ed25519 signing uses transient keys unless explicitly persisted.
- Certificates are URL-safe Base64 encoded and time-limited.
- Never commit secrets or credential material.
- Never commit large model artifacts unintentionally.
