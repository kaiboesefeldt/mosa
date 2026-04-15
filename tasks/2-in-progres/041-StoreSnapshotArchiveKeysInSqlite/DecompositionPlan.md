This file records the intended later increments.

The task board rules treat the lowest open `subNNN` as the current executable increment. Multiple future subtasks may
therefore exist at once without making the last verification step active prematurely.

Planned follow-up increments after `sub001` is closed:

1. Implement the SQLite-backed snapshot archive-key persistence and productive wiring.
   This includes the new structured-state table/store implementation, bootstrap behavior for the first key, DI rewiring,
   and preserving the existing snapshot-facing interfaces and semantics.

2. Verify and close the migration.
   This includes documentation/help-surface updates, targeted regression checks for restart and historical-key lookup
   behavior, `./gradlew check`, and confirmation that `mosa_snapshot_archive_keys.json` no longer appears as a productive
   runtime artifact.
