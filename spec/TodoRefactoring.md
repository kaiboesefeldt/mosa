# Refactoring Opportunities

## Client
- `client/src/jsMain/kotlin/straightway/mosa/client/assets/FontAwesomeResources.kt:5` – The external module currently returns `dynamic`. Prefer a typed external (e.g., `Unit` return) to remove the implicit dynamic access pattern.

## General
- `server/src/main/kotlin/straightway/mosa/CommandLineArgumentStore.kt:3` – Usage across modules relies on raw `Any?` lookups, pushing casts to callers. Introduce typed accessors (e.g., `getBoolean`) to avoid scattered casts and unchecked assumptions.
