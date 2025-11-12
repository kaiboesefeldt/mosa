# Refactoring Opportunities

## Client

## General
- `server/src/main/kotlin/straightway/mosa/CommandLineArgumentStore.kt:3` – Usage across modules relies on raw `Any?` lookups, pushing casts to callers. Introduce typed accessors (e.g., `getBoolean`) to avoid scattered casts and unchecked assumptions.
