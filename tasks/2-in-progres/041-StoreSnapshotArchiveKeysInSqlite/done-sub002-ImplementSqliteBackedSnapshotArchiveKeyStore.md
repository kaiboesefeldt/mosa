Implement the productive move of snapshot archive-key persistence into SQLite-backed structured state.

Scope of this subtask:

- add the snapshot archive-key table and any tightly related persistence helpers
- implement the new SQLite-backed store behind the existing snapshot-facing interfaces
- wire the new table through structured-state initialization
- replace the productive file-backed store binding in DI
- adapt and extend tests for active-key bootstrap, restart persistence, and historical-key lookup

Required outcomes:

- productive server wiring no longer depends on `FileBackedFreezeSnapshotArchiveKeyStore`
- snapshot services continue to use the existing domain-facing interfaces without SQL details leaking upward
- active-key lookup and historical-key lookup preserve current behavior across restart
- the first key is bootstrapped safely inside the structured-state persistence path when no key exists yet

Constraints:

- keep snapshot cryptology and external behavior unchanged
- do not move server-only snapshot concerns into `shared/`
- do not use this increment to perform the final docs/help cleanup except where compile or tests require minimal touch-ups

Verification expectations for this subtask:

- targeted tests cover the new persistence semantics, especially restart and lookup behavior
- all structured-state and snapshot consumers touched by the store swap are revisited explicitly
- any deferred documentation/help changes are listed precisely for `sub003`
