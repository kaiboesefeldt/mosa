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

## 6. Build, Run, and Verification Commands
### 6.1 Common commands
- Build all: `./gradlew build`
- Run server: `./gradlew :server:run`
- Create server fat jar: `./gradlew :server:shadowJar`
- Run CLI example: `./gradlew :cli:run --args="search --pod http://localhost:8080 --topic 'query'"`
- Run all tests: `./gradlew test`
- Run module tests: `./gradlew :server:test` / `./gradlew :client:test`
- Run single test: `./gradlew :server:test --tests straightway.mosa.SomeTest`
- Coverage run: `./gradlew -Pkover clean test koverXmlReport koverVerify --no-daemon --stacktrace --continue`
- Format all: `./gradlew formatKotlin`
- Lint all: `./gradlew lintKotlin`
- Clean outputs: `./gradlew clean`

### 6.2 Notes
- Coverage report path: `build/reports/kover/report.xml`
- `straightway.mosa.content.QualityTest` may be excluded for intermediate runs unless embedding/vector-store behavior is changed.
- Gradle warnings can be ignored.

## 7. Repository Topology
- `server/src/main/kotlin`: Ktor + Koin server (`Main.kt`, `AppModule.kt`, controllers/services/auth/vector components).
- `client/src/jsMain/kotlin`: Kotlin/JS web client.
- `cli/src/main/kotlin/straightway/mosa/cli`: JVM CLI (`push`, `search`, `cert`, `identity`).
- `shared/src/commonMain/kotlin`: shared multiplatform DTOs/models.
- `models/`: optional DJL/ONNX assets (keep large model files out of commits unless intentional).
- `mosa_lucene_index/`: persistent local Lucene KNN index (delete to reset local state).

## 8. Runtime Architecture Snapshot
### 8.1 Server
- Ktor 3.2 Netty server with Koin 4.1 DI.
- `OnnxLocalEmbeddingService` uses DJL + ONNX Runtime with `efederici/multilingual-e5-small-4096`.
- `LuceneLocalVectorStore` persists vectors in `MMapDirectory` at `mosa_lucene_index`.
- Auth uses MIS-JWT bearer tokens via `Auth0TokenUtil`.
- Routes:
  - Open: `/v1/approvegid`
  - Auth required: `/v1/publish`, `/v1/reply`, `/v1/query`, `/v1/queryon`
- `WebClientController` serves SPA from `static/`, injects `window.MOSA_POD_BASE`, and serves source maps only when `MOSA_SERVE_SOURCE_MAPS=true`.
- `shadowJar` builds `mosa-server-all.jar` and depends on copied client assets.

### 8.2 Client
- Kotlin/JS IR app with Koin + Ktor JS client + coroutines.
- Entry point: `straightway.mosa.client.Main`.
- `copyClientFiles` copies production bundle into server static resources before `processResources`/`shadowJar`.
- HTML template: `client/src/jsMain/resources/index.html`.
- Font Awesome Free included via npm.

### 8.3 CLI
- `CliMain.kt` with `kotlinx-cli` subcommands.
- `MosaRestClient` uses Ktor CIO + Jackson, requests pod certificates, builds MIS-JWTs.
- Default pod: `http://localhost:8080` (`--pod` to override).
- Keypair via `--keypair` (Base64-URL without padding) or ephemeral identity generation.

## 9. Security Constraints
- Ed25519 signing uses `TransientEd25519Factory`; keys are ephemeral unless explicitly persisted.
- `PodCertificateService` signs URL-safe Base64 encoded certificates with finite validity (default 7 days via `AuthenticationController`).
- Never commit secrets or credential material.
- Never commit large model artifacts unintentionally.
