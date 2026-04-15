This file records the intended later increments.

The task board rules treat the lowest open `subNNN` as the current executable increment. Multiple future subtasks may
therefore exist at once without making the last verification step active prematurely.

Planned follow-up increments after `sub001` is closed:

1. Move shared and server code to the approved target namespaces.
   This includes package declarations, imports, DI wiring, tests, and any `spec/Architecture.md` updates that become concrete during the move.

2. Migrate client usage and remove residual content leakage.
   This includes client services, pod access, UI controllers, tests, and any stale naming that preserves the old false ownership.

3. Verify the integrated cleanup.
   This includes `./gradlew check`, final inspection of remaining `straightway.mosa.content` contents, documentation review in `spec` and server docs, and confirmation that no new third-party dependencies were introduced.
