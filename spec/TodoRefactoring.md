# Refactoring Opportunities

## Server
- `server/src/main/kotlin/straightway/mosa/auth/PodCertificateService.kt:68-88` – `String?.urlDecoded()` assumes Base64-URL input for `inf`, but callers pass raw strings today. Introduce explicit encoding responsibilities and make the contract clear so optional info does not crash decoding.
- `server/src/main/kotlin/straightway/mosa/vectorstore/LuceneLocalVectorStore.kt:1-434` – The monolithic file wraps indexing, search execution, pagination state, and concurrency control in one class. Split responsibilities (e.g., reader cache, query planner, pagination registry) so each piece stays small and independently testable.

## Shared
- `shared/src/commonMain/kotlin/straightway/mosa/util/BitStream.kt:24-41` – Using modulus to fold `nextBits*` results into ranges introduces bias for non power-of-two bounds. Switch to rejection sampling to keep generated names statistically uniform.
- `shared/src/commonMain/kotlin/straightway/mosa/util/BitStream.kt:61` – Returning zero when the source byte array is exhausted silently corrupts downstream randomness. Surface depletion explicitly so callers can refresh entropy rather than unknowingly reusing zeros.
- `shared/src/commonMain/kotlin/straightway/mosa/util/Permutations.kt:11-16` – Building permutations via repeated `drop`/`take` creates quadratic allocations. Rewrite using indexed insertion into a mutable buffer to cut GC pressure in name generation.

## Client
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt:1-299` – The hook mixes DI lookups, coroutine orchestration, and view-model logic in one monolith, making it hard to test or extend. Extract a state machine/service layer and keep the hook focused on React state wiring.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt:301` – A global `MainScope` survives component unmounts, leaking coroutines. Replace with `CoroutineScope` tied to `useEffect` cleanup or leverage `CoroutineScope` from `MainScope()` stored in state.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityLandingView.kt:1-210` – One React component renders the full dialog, handles tool menus, and wires actions. Split into smaller, focused components (header, carousel, tools) to improve readability and reuse.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt:112-182` – Error-handling blocks repeat the same logging/message formatting. Centralise this into a helper to ensure consistent UX and simplify upcoming features.
- `client/src/jsMain/kotlin/straightway/mosa/client/browser/SubtleCrypto.kt:8-46` – The wrapper still models WebCrypto calls via explicit `Promise` casts. Replace the `Promise` plumbing with typed suspend helpers (e.g., `await` on `web.crypto.SubtleCrypto`) so the client stays promise-free as required.
- `client/src/jsMain/kotlin/straightway/mosa/client/browser/SubtleCrypto.kt:35` – `generateKey` accepts an `algo: String` but is always passed a dynamic object. Align the signature with WebCrypto types (e.g., `AlgorithmIdentifier`) to avoid unsafe casts.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IdentityGenerator.kt:11-18` – Building the algorithm via `js("({ name: 'Ed25519' })")` and handing dyn values to `generateKey` is non-idiomatic Kotlin/JS. Switch to the typed `web.crypto` bindings (or wrap the dynamic call) so the compiler can help.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentitySession.kt:73-244` – Repeated calls to `window.asDynamic()` for console and config access are non-idiomatic React/Kotlin. Introduce typed helpers (e.g., `window.console` plus a typed config injection) so runtime typos become compile-time errors.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityLandingView.kt:63` – Accessing `Event.target` through `asDynamic().closest` bypasses the DOM types. Wrap the lookup in a typed helper or use `target.closest` via the `Element` interface to stay type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IndexedDbIdentityStore.kt:18-181` – The store API is entirely `dynamic` despite available DOM/IndexedDB externals. Replacing these with typed interfaces (`IDBDatabase`, `IDBObjectStore`, etc.) will simplify reasoning and remove unchecked casts.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IndexedDbIdentityStore.kt:1-181` – Persistence, migration, serialization, and error handling all live in one coroutine-heavy block. Extract smaller adapters (schema manager, record mapper, async bridge) to reduce cognitive load and aid testing.
- `client/src/jsMain/kotlin/straightway/mosa/client/browser/BrowserPrefs.kt:7-28` – Injecting `dynamic` navigators and using `js("Array.isArray")` is non-idiomatic. Adopt the typed `Navigator` externals and Kotlin collection helpers to eliminate unchecked `dynamic` usage.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IdentityTransferService.kt:20-45` – Falling back to `window.asDynamic().URL` is fragile. Introduce a typed URL factory (e.g., wrap `kotlinx.browser.window.URL`) to keep the code type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/i18n/Translator.kt:46-68` – The translator loads resources through `dynamic` `require.context`. Encapsulate that behind a typed abstraction or generate bindings so translation lookups are checked at compile time.
- `client/src/jsMain/kotlin/straightway/mosa/client/util/TextEncoder.kt:6-12` – Exposing the raw `dynamic` return type forces callers to `unsafeCast`. Replace with the typed `TextEncoder` declaration from the stdlib (or wrap the cast) so downstream code stays type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/assets/FontAwesomeResources.kt:7-13` – The external module currently returns `dynamic`. Prefer a typed external (e.g., `Unit` return) to remove the implicit dynamic access pattern.

## General
- `CommandLineArgumentStore` usage across modules relies on raw `Any?` lookups, pushing casts to callers. Introduce typed accessors (e.g., `getBoolean`) to avoid scattered casts and unchecked assumptions.
