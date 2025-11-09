# Refactoring Opportunities

## Client
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityLandingView.kt:27` – One React component still renders the full dialog, handles tool menus, and wires actions. Split into smaller, focused components (header, carousel, tools) to improve readability and reuse.
- `client/src/jsMain/kotlin/straightway/mosa/client/browser/SubtleCrypto.kt:35` – `generateKey` accepts an `algo: String` but is always passed a dynamic object. Align the signature with WebCrypto types (e.g., `AlgorithmIdentifier`) to avoid unsafe casts.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IdentityGenerator.kt:11` – Building the algorithm via `js("({ name: 'Ed25519' })")` and handing dynamic values to `generateKey` is non-idiomatic Kotlin/JS. Switch to the typed `web.crypto` bindings (or wrap the dynamic call) so the compiler can help.
- `client/src/jsMain/kotlin/straightway/mosa/client/ui/IdentityLandingView.kt:58` – Accessing `Event.target` through `asDynamic().closest` bypasses the DOM types. Wrap the lookup in a typed helper or use `target.closest` via the `Element` interface to stay type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IndexedDbIdentityStore.kt:14` – The store API is still `dynamic` despite available DOM/IndexedDB externals. Replacing these with typed interfaces (`IDBDatabase`, `IDBObjectStore`, etc.) will simplify reasoning and remove unchecked casts.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IndexedDbIdentityStore.kt:14` – Persistence, migration, serialization, and error handling all live in one coroutine-heavy block. Extract smaller adapters (schema manager, record mapper, async bridge) to reduce cognitive load and aid testing.
- `client/src/jsMain/kotlin/straightway/mosa/client/browser/BrowserPrefs.kt:7` – Injecting `dynamic` navigators and using `js("Array.isArray")` is non-idiomatic. Adopt the typed `Navigator` externals and Kotlin collection helpers to eliminate unchecked `dynamic` usage.
- `client/src/jsMain/kotlin/straightway/mosa/client/identity/IdentityTransferService.kt:25` – Falling back to `window.asDynamic().URL` is fragile. Introduce a typed URL factory (e.g., wrap `kotlinx.browser.window.URL`) to keep the code type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/i18n/Translator.kt:43` – The translator loads resources through `dynamic` `require.context`. Encapsulate that behind a typed abstraction or generate bindings so translation lookups are checked at compile time.
- `client/src/jsMain/kotlin/straightway/mosa/client/util/TextEncoder.kt:5` – Exposing the raw `dynamic` return type forces callers to `unsafeCast`. Replace with the typed `TextEncoder` declaration from the stdlib (or wrap the cast) so downstream code stays type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/assets/FontAwesomeResources.kt:5` – The external module currently returns `dynamic`. Prefer a typed external (e.g., `Unit` return) to remove the implicit dynamic access pattern.

## General
- `server/src/main/kotlin/straightway/mosa/CommandLineArgumentStore.kt:3` – Usage across modules relies on raw `Any?` lookups, pushing casts to callers. Introduce typed accessors (e.g., `getBoolean`) to avoid scattered casts and unchecked assumptions.
