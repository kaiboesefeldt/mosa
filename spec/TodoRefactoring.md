# Refactoring Opportunities

## Client
- `client/src/jsMain/kotlin/straightway/mosa/client/i18n/Translator.kt:43` – The translator loads resources through `dynamic` `require.context`. Encapsulate that behind a typed abstraction or generate bindings so translation lookups are checked at compile time.
- `client/src/jsMain/kotlin/straightway/mosa/client/util/TextEncoder.kt:5` – Exposing the raw `dynamic` return type forces callers to `unsafeCast`. Replace with the typed `TextEncoder` declaration from the stdlib (or wrap the cast) so downstream code stays type-safe.
- `client/src/jsMain/kotlin/straightway/mosa/client/assets/FontAwesomeResources.kt:5` – The external module currently returns `dynamic`. Prefer a typed external (e.g., `Unit` return) to remove the implicit dynamic access pattern.

## General
- `server/src/main/kotlin/straightway/mosa/CommandLineArgumentStore.kt:3` – Usage across modules relies on raw `Any?` lookups, pushing casts to callers. Introduce typed accessors (e.g., `getBoolean`) to avoid scattered casts and unchecked assumptions.
