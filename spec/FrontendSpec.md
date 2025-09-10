### Project Specification: Mosa Frontend Setup

**Objective:**
Set up a Kotlin/JS-based frontend using React, integrated into an existing Kotlin/Ktor backend project. The frontend should be served from the same executable bundle as the backend and use shared data classes for consistency.

**Tech Stack:**

* **Frontend Language:** Kotlin/JS
* **Frontend Framework:** React (via Kotlin Wrappers)
* **Backend:** Kotlin with Ktor
* **Build Tool:** Gradle
* **Testing:** kotlin-test for unit and component tests

**Project Structure:**

1. **Backend Module:**

    * Location: `src/main/kotlin` (already existing Ktor server)
    * Function: Provides HTTP endpoints and serves the frontend assets.

2. **Frontend Module:**

    * Location: `client/src/jsMain/kotlin`
    * Function: Contains all React components and frontend logic written in Kotlin/JS.

3. **Shared Module:**

    * Location: `shared/src/commonMain/kotlin`
    * Function: Defines data classes shared between frontend and backend to ensure consistency and reduce duplication.

**Build and Deployment:**

* Use Gradle to produce a single executable JAR (Fat JAR) that includes both the backend server and the transpiled frontend assets.
* The Ktor server serves the frontend from the root path (`/`) using the static resources bundled in the JAR.
* Ensure that the build process compiles the Kotlin/JS frontend, places the generated JS/CSS files in `build/resources/main/static`, and packages everything into the final JAR.

**Testing:**

* Implement unit and component tests using `kotlin-test` to validate frontend logic and React components.
* Ensure that shared data classes are consistently tested in both frontend and backend contexts.

**Open Source Licensing:**

* The entire project is to be released under the Apache License, version 2.0.

**Implementation Notes:**

1. **Gradle Configuration:**

    * Use the Kotlin Multiplatform Gradle plugin with targets `jvm` (for backend), `js(IR)` (for frontend), and `common` (for shared module).
    * Add build tasks that automatically copy the generated JS bundle from `client/build/distributions` into `build/resources/main/static`.

2. **Frontend Setup:**

    * Initialize React components with `kotlin-wrappers` (`kotlin-react`, `kotlin-react-dom`).
    * Use `react-router-dom` (via wrapper) for navigation between views (topic definition, query results, thread view).
    * Handle REST calls to Ktor using `kotlinx.coroutines` and `kotlinx.serialization`.

3. **Identity Management:**

    * Use the WebCrypto API via Kotlin/JS externals to generate and store key pairs securely in the browser.
    * Persist keys in `IndexedDB` using `kotlinx.browser` APIs.
    * Implement certificate requests against `/v1/approvegid` and inject MIS-JWT automatically into every API request.

4. **UI Structure:**

    * **Main Views:** Topic definition form, query result list, thread view (replies), and publishing form. A mockup implementation can be found in [FrontendPrototype.html](./FrontendPrototype.html)
    * **Error Handling:** Show clear error messages if a post is rejected (`422`) with pod hints.
    * **Design:** Keep components lightweight and focus on readability; semantic grouping is key.

5. **Testing Setup:**

    * Use `kotlin-test` for unit tests.
    * For React components, integrate `react-testing-library` through Kotlin wrappers.
    * Write regression tests for posting, replying, and querying flows.

6. **Deployment:**

    * Provide a Gradle task `:shadowJar` (via `com.github.johnrengelman.shadow`) to build a single Fat JAR.
    * Document startup procedure: `java -jar mosa-all.jar` runs backend and frontend together.

## Implementation Guidance

### 1) Gradle multi-module layout

```
root
├─ build.gradle.kts (version catalogs, common plugins)
├─ settings.gradle.kts (includes :server, :client, :shared)
├─ server/        (Ktor JVM)
├─ client/        (Kotlin/JS React)
└─ shared/        (Kotlin Multiplatform data classes)
```

**Version catalog (libs.versions.toml)** – define kotlin, ktor, koin, wrappers, serialization, shadow.

### 2) Shared module (data classes)

- `shared/build.gradle.kts`
- Place DTOs in `shared/src/commonMain/kotlin/...` using `@Serializable` to guarantee identical JSON on both sides.
- ONLY move DTOs from the server module to shared, that are required for both client and server to use/provide the REST API of the Mosa Pod.

### 3) Server module (Ktor + static serving + fat JAR)

- `server/build.gradle.kts`
- Static routing (Ktor)

### 4) Client module (Kotlin/JS React)

- `client/build.gradle.kts`
- File layout
  ```
  client/src/jsMain/kotlin/App.kt
  client/src/jsMain/resources/index.html
  ```
  - `index.html` — minimal shell that loads `app.js`.
  - `App.kt` — simple React root & routes (topic input, results list, publish dialog).

### 5) MIS identity in the browser (keys, certs, JWT)

* **Key generation:** Prefer **WebCrypto** (`window.crypto.subtle.generateKey` with `{ name = "Ed25519" }`).
* **Storage:** Keep private keys **non-extractable** if possible; store references/cryptoKey in **IndexedDB** (default for WebCrypto) and metadata (GID, cert exp) in IndexedDB as well. If you need export (for backup), use an opt-in flow and encrypt with a passphrase (PBKDF2 + AES-GCM) before storing.
* **GID:** `SHA-256(pubKeyRaw)` → base64url (no padding). Use WebCrypto `digest("SHA-256", publicKeyBytes)`.
* **Certificate flow:** Call `POST /v1/approvegid` with the GID (no bearer). Persist returned cert (JSON) with `exp`.
* **JWT minting:** Build header `{alg:"EdDSA", typ:"JWT"}` and payload `{pub, cert, exp, mvs:"1"}`; base64url(header) + "." + base64url(payload), sign with **Ed25519** using WebCrypto. **Fallback:** use `tweetnacl.sign.detached` if a browser lacks Ed25519 in WebCrypto.
* **Bearer usage:** Attach the JWT via `Authorization: Bearer <token>` on all `/v1/**` calls.
* **Strict time:** Always compare `now >= exp` (no clock skew) before sending.

### 6) HTTP client layer

Create a thin `ApiClient` in the client module that:

* Injects bearer automatically (reads latest valid JWT from store, re-mints if near expiry).
* Centralizes fetch/JSON (kotlinx.serialization), handles `422` with body hints, and `429` (`Retry-After`).
* Exposes methods: `publish(text)`, `reply(rootId, text)`, `query(text, limit, since, root?)`, `queryOn(id)` and `approvegid(gid)`.

### 7) UI slices (MVP)

* **TopicSearchView**: textarea + submit → calls `query`; results list with infinite scroll (`queryOn`).
* **ThreadView**: root + replies (ordered by storage time). Reply form validates on-topic hinting.
* **ComposePostDialog**: publish new post; show rejection reason if `422`.
* **IdentityBadge**: shows short GID, cert expiry, renew button.

### 8) State management

Keep it simple: React `useState`/`useReducer`. Introduce a tiny store only if needed (e.g., `kotlin-redux` wrapper later). Persist lightweight UI prefs in `localStorage`; crypto in IndexedDB.

### 9) Testing strategy

* **Logic tests** (`kotlin-test`): base64url, time checks, JWT construction (sign inputs), certificate parsing.
* **Component tests**: render with `react-dom/test-utils` (or `@testing-library/react` via NPM); simulate input and assert DOM text.
* **API tests**: mock `fetch` and assert headers/JSON. CI runs `:client:test` and `:server:test`.

### 10) Build & one-binary delivery

* Default pipeline: `:client:jsBrowserDistribution` → copied into `server/build/resources/main/static` → `:server:shadowJar` produces `mosa-server-all.jar`.
* Run: `java -jar mosa-server-all.jar` → serves API and SPA on the same port.

### 11) Configuration & environments

* Inject minimal config via server-rendered meta or `/v1/info` (later): default pod URL, feature flags.
* Avoid multiple env files; prefer server-side defaults and client-side overrides via query string only for dev.

### 12) Lint/format & CI

* Reuse ktlint via `org.jmailen.kotlinter` root plugin.
* GitHub Actions job: cache Gradle, run `./gradlew clean check :client:jsBrowserDistribution :server:shadowJar` and upload the fat JAR as artifact.

### 13) Accessibility & i18n (baseline)

* Use semantic HTML in components, keyboard focus traps in dialogs, `aria-*` on interactive elements.
* Wrap copy in a simple i18n helper (keys → strings) to enable later locale expansion.

### 14) Observability (later)

* Add a lightweight log sink (console + server `/v1/log` in dev only). No JWTs or secrets in logs.

### 15) Security footnotes

* Never put tokens in URLs. Only `Authorization` header.
* Don’t log JWTs/certs. Respect strict-expiry semantics.
* CSP header from Ktor: default-src 'self'; connect-src configured for pod federation as needed.
