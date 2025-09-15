# Repository Guidelines

## Project Structure & Module Organization
- Root server (Ktor, Koin): `src/main/kotlin`, resources in `src/main/resources`, tests in `src/test/kotlin`.
- CLI tool (Kotlin/JVM): `cli/src/main/kotlin/straightway/mosa/cli`.
- Web client (Kotlin/JS): `client/src/jsMain/kotlin`; built assets are copied to `build/resources/main/static`.
- Models for local embeddings: `models/` (loaded by DJL/ONNX).
- Vector index persists in `mosa_lucene_index/`.

## Architecture Overview
- Ktor + Netty (JVM 19), DI via Koin (`AppModule.kt`).
- MIS-JWT bearer auth, Jackson JSON, controller routing through `BaseController`.
- Embeddings via ONNX/DJL (`EmbeddingService`), Lucene KNN vector store (`VectorStore`).

## Build, Test, and Development Commands
- Build all modules: `./gradlew build` — builds server, client bundle, and CLI.
- Run server locally: `./gradlew run` — starts Ktor on `http://localhost:8080`.
- Run CLI: `./gradlew :cli:run --args="--help"` (or `:cli:run --args="search --pod http://localhost:8080 --q 'text'"`).
- Run tests: `./gradlew test` — JUnit 5 + kotlin-test + MockK; single test: `./gradlew test --tests "ClassName[.method]"`.
- Verification/cleanup: `./gradlew check` and `./gradlew clean`.
- Format/lint: `./gradlew formatKotlin` (auto-fix) and `./gradlew lintKotlin`.

## Coding Style & Naming Conventions
- Kotlin 2.x, ktlint via `org.jmailen.kotlinter` (auto-runs before compile).
- Indentation/spaces follow ktlint defaults; avoid wildcard imports.
- Packages under `straightway.mosa.*`; production code in `src/main/kotlin`, tests mirror packages in `src/test/kotlin`.
- Filenames match top-level class/object (e.g., `ContentService.kt`).
- Make sure you don't introduce trailing spaces in lines, don't use tabs, don't have additional newlines at the end of files
- If you introduce new libs, always make sure you use the most recent stable version, if possible.

## Testing Guidelines
- Place tests under `src/test/kotlin/...`; name files `*Test.kt` (e.g., `ContentControllerTest.kt`).
- Prefer fast, deterministic unit tests; use MockK for collaborators; Ktor test host for endpoint tests.
- No strict coverage gate today; keep service/controller logic covered and add regression tests for bugs.
- Test data assets live in `src/test/resources/...`.

## API & Auth
- All routes under `/v1`; bearer MIS-JWT required for most endpoints.
- Unauthenticated: `POST /v1/approvegid` (request pod certificate for a GID).

## Commit & Pull Request Guidelines
- Commits: short, imperative summaries (e.g., "Add delete button", "Fix focus handling").
- PRs: clear description, scope, and rationale; link issues; include screenshots for UI/client changes; note test coverage and manual steps.
- Keep changes focused; separate refactors from behavior changes when possible.

## Security & Configuration Tips
- Authentication uses MIS-JWT; server decodes bearer tokens via `TokenUtil` and Ed25519 (BouncyCastle backend).
- Local embedding model files in `models/`; Lucene index in `mosa_lucene_index/` (delete to reset state during dev).
- Avoid committing secrets or key material; CLI provides `CreateIdentityCommand`, `GetCertCommand`, `PublishCommand`, and `SearchCommand`.
