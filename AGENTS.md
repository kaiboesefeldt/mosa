# MOSA Agent Guide

## General
- Answer in the same language as the request formulated by the user.

## Module Topology
- `server/src/main/kotlin` – Ktor + Koin application entry point (`Main.kt`) with controllers, services, and DI wiring in `AppModule.kt`.
- `client/src/jsMain/kotlin` – Kotlin/JS web client; Webpack output copied to `server/build/resources/main/static` via the `copyClientFiles` Gradle task.
- `cli/src/main/kotlin/straightway/mosa/cli` – JVM CLI built on `kotlinx-cli`, reusing shared DTOs and crypto utilities.
- `shared/src/commonMain/kotlin` – Multiplatform models for auth (`PodCertificate`, `AuthenticationApprovalRequest`) and content (`PublishRequest`, `QueryRequest`, etc.) consumed by server, client, and CLI.
- `models/` – Optional DJL/ONNX assets; `OnnxLocalEmbeddingService` defaults to the Hugging Face `multilingual-e5-small-4096` model, but you can repoint it here; keep large files out of source control updates unless intentional.
- `mosa_lucene_index/` – Lucene KNN index persisted on disk; delete to reset local state while developing.

## Server (Ktor 3.2)
- Netty engine with DI through Koin 4.1 (`AppModule.kt`); wiring registers `EmbeddingService`, `VectorStore`, crypto utilities, controllers, and the `PodCertificateService`.
- `OnnxLocalEmbeddingService` (DJL 0.33 + ONNX Runtime) embeds text using DJL's Hugging Face integration and the `efederici/multilingual-e5-small-4096` model, propagating its vector dimension into the Lucene store.
- `LuceneLocalVectorStore` persists content vectors in an `MMapDirectory` targeting `mosa_lucene_index`, supports paged KNN queries and thread filtering.
- Authentication relies on MIS-JWT bearer tokens verified by `Auth0TokenUtil`, which checks Ed25519 signatures, pod certificates, and claim constraints.
- `/v1/approvegid` (open) issues short-lived pod certificates; `/v1/publish`, `/v1/reply`, `/v1/query`, `/v1/queryon` require bearer auth and are handled in `ContentController`.
- `WebClientController` serves the compiled SPA from classpath `static/`, injects `window.MOSA_POD_BASE`, and gates source-map serving behind `MOSA_SERVE_SOURCE_MAPS=true`.
- Gradle `shadowJar` assembles an all-in-one server artifact (`mosa-server-all.jar`) and depends on copying the latest client bundle.

## CLI Tooling
- Entry point `CliMain.kt` sets up `kotlinx-cli` subcommands: `push` (publish content), `search` (semantic query), `cert` (explicit certificate request), and `identity` (generate keys in PEM or Base64-URL).
- `MosaRestClient` talks to pods via Ktor CIO HTTP client with Jackson, automatically obtains a pod certificate for the active key pair and builds MIS-JWTs with `JwtBuilder`.
- Default pod endpoint is `http://localhost:8080`; override via `--pod`. Provide an Ed25519 key pair with `--keypair` (Base64-URL, no padding) or let the CLI mint an ephemeral identity.

## Web Client
- Kotlin/JS IR application using Koin, Ktor HTTP client JS, coroutines, and shared DTOs. Entry point `straightway.mosa.client.Main` bootstraps DI defined in `AppModule.kt`.
- UI renders a React-based identity generator that mints Ed25519 key pairs in-browser, requests pod certificates through `PodAccess`, and assembles MIS-JWTs with `MisJwtService` for display.
- Built assets live under `client/build/…`; `copyClientFiles` task lifts the production bundle into the server resources directory before `processResources`/`shadowJar`.
- Static HTML template sits in `client/src/jsMain/resources/index.html`; the server injects runtime base URLs so Pod location remains configurable.
- Font Awesome Free is bundled via NPM for icon usage in UI work.

## Build & Run
- Build everything: `./gradlew build` (runs ktlint checks, compiles server, CLI, client bundle, and shared module).
- Run server locally: `./gradlew :server:run` (Netty on `http://localhost:8080`). For a packaged jar use `./gradlew :server:shadowJar`.
- Run CLI ad-hoc: `./gradlew :cli:run --args="search --pod http://localhost:8080 --topic 'query'"`.
- Execute tests by module (`./gradlew :server:test`, `./gradlew :client:test`) or all at once with `./gradlew test`. Single test example: `./gradlew :server:test --tests straightway.mosa.SomeTest`.
- Run test with code coverage: `./gradlew -Pkover clean test koverXmlReport koverVerify --no-daemon --stacktrace --continue` (coverage report under `build/reports/kover/report.xml`)
- Formatting/linting: `./gradlew :server:formatKotlin`, `./gradlew :server:lintKotlin` (repeat per module) or run across all via `./gradlew formatKotlin` / `./gradlew lintKotlin`.
- Clean build outputs with `./gradlew clean`; reset vector index manually by removing `mosa_lucene_index/`.
  * If you make intermediate tests, you may exclude `straightway.mosa.content.QualityTest`, as it is long-running and only of relevance, if you change the embedding or the vector store properties.
- Gradle warnings can be safely ignored

## Coding Standards
- Kotlin 2.2, ktlint enforced via `org.jmailen.kotlinter`; avoid wildcard imports, keep indentation and spacing at defaults, and do not add trailing whitespace or superfluous EOF newlines.
- Expressive naming over comments; prefer extracting helpers instead of annotating code. Maintain `straightway.mosa.*` packaging and align file names with their top-level declarations.
- Tests live beside code in each module under `src/test/kotlin`. Keep them fast, deterministic, and focused—one scenario per test.
- If you introduce new libs, always make sure you use the most recent stable version, if possible. Look up the most recent version using a web search!!
- Don't use code comments at all! Instead, write the code so expressive, that comments are not required, e.g. by naming of code entities and extracted functions, that are named to make the functionality clear.
- Avoid over-engineering!
- Avoid premature optimization!
- Prefer using concrete types instead of `dynamic`.
- Prefer using `suspend` functions instead of promises.
- Avoid using nullable types as far as reasonable.
- If necessary, reflect any major changes in the software architecture or the code structure in this `AGENTS.MD` file.
- Don't use reflection!
- Clearly separate UI-related code from the rest.
  1. Make the ui related code as small as possible.
  2. Only make sure, ui-related code compiles, but don't write tests for this code. 
- If you develop business logic, you MUST work test-driven, iteratively in very small increments, i.e.
  1. Plan a small iteration, that brings you closer to the solution
  2. FIRST write one MINIMAL unit test case, that tests the small functionality increment to develop, and see it red
  3. THEN write the MINIMAL production code, that makes the test green, and see it green
  4. THEN, without changing the test, clean up the production code, make it nice and minimal
  5. THEN, without changing the production code, clean up the test code
  6. If not yet finished, go back to step 1.
- Final quality gate: ALWAYS make sure, `gradlew check` tuns successfully 
- Strive for 100% test coverage, but:
  1. Be aware, that test coverage measurements sometimes yields inaccurate results, e.g. for data classes or for classes annotated with `@Serializable`.
  2. Notice, that coverage for KotlinJs code is currently not measured. 

## Testing Guidelines
- Prefer fast, deterministic unit tests; use MockK for collaborators; Ktor test host for endpoint tests.
- No strict coverage gate today; keep service/controller logic covered and add regression tests for bugs.
- In KotlinJs tests, use `runTest`.
- Strictly handle one minimal test case per test.
- Make sure, tests DON'T succeed, only because preconditions are NOT met (e.g. `val c = createComponent() ?: return@runTest)`.
- Name tests using kotlin's backtick notation, e.g. "fun `invokes connect handler when interaction finishes`()"
- It is OK to make minor, functionally neutral changes to production code to simplify testing

## Security Notes
- Ed25519 signing implemented through `TransientEd25519Factory`; keys are ephemeral in-memory unless you persist them explicitly.
- `PodCertificateService` signs certificates using the server-held key, encoding fields as URL-safe Base64. Certificates expire after the configured duration (default 7 days via `AuthenticationController`).
- Credential material or large models should never ship in commits. Use `.env`/runtime config for secrets and remember the CLI can mint identities locally.
