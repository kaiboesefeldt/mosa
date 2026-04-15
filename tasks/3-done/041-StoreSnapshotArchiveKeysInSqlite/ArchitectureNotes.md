# Architecture Notes

## Readiness

- Subtask decomposition decision: required.
- Rationale: the task crosses three distinct implementation concerns that should not be collapsed into one opaque increment:
  - the persistence shape for snapshot archive keys must be made explicit before code movement starts
  - the productive store swap touches snapshot internals, structured-state initialization, and DI wiring together
  - documentation/help-surface cleanup and final verification should stay as a separate closing increment so incomplete implementation work cannot hide inside a "verification" step
- Planned increments:
  - `sub001`: define the SQLite persistence shape, ownership boundaries, and migration assumptions precisely enough that implementation can proceed without re-cutting the data model mid-change
  - `sub002`: implement the new structured-state-backed archive-key store and productive wiring while preserving existing snapshot service semantics
  - `sub003`: update architecture and operational documentation, run final verification, and confirm no productive JSON-file expectations remain
- Implementation readiness decision: `sub001` is only complete when the persistence shape, ownership, and bootstrap path below are treated as fixed inputs for `sub002`.

## Fixed Design For `sub002`

### Persistence Shape

- Use one snapshot-owned structured-state table named `SnapshotArchiveKeysTable`.
- Place the table in `server/src/main/kotlin/straightway/mosa/identity/privacy/snapshot/`.
- The table stores exactly these persistence concerns:
  - `keyId` as the primary identifier and unique lookup key
  - `secret` as the persisted archive-key secret in server-local encoded form
  - `isActive` as the explicit active-key marker
  - `createdAt` as persisted creation time for stable inspection and deterministic restart reasoning
- Do not introduce a second metadata table for this task.
- Do not store snapshot archive keys in `shared/`; they remain server-only runtime state.

### Active-Key Rule

- The implementation must model "exactly one active key" directly in the table behavior.
- For this task, use the single-table design with one active row marked by `isActive = true`.
- `activeKey()` must resolve from that active row and must not synthesize state outside persistence.
- `findByKeyId(...)` must resolve from the same table and continue to expose historical keys without changing snapshot-service contracts.
- If the implementation cannot enforce the single-active-row invariant cleanly with the chosen Exposed/SQLite shape, stop and raise a new architecture issue instead of silently switching to a second table or service-managed pointer.

### Ownership And Package Boundaries

- Keep the domain-facing interfaces unchanged:
  - `FreezeSnapshotArchiveKeyStore`
  - `FreezeSnapshotActiveArchiveKeyProvider`
- Implement the new infrastructure as `SqliteFreezeSnapshotArchiveKeyStore` in the snapshot package.
- Keep Exposed table definitions and SQL details inside the snapshot area with that store implementation.
- `StructuredStateAppModule` remains the composition root that initializes all structured-state tables, including the new snapshot table.
- This task does not widen `StructuredStateContext` and does not turn the structured-state module into the owner of snapshot-specific persistence behavior.

### Bootstrap Ownership

- First-key bootstrap is owned by `SqliteFreezeSnapshotArchiveKeyStore`, not by `FreezeSnapshotService` and not by DI setup code.
- The store may depend on `StructuredStateDatabase` directly so it can perform bootstrap atomically inside structured-state persistence.
- Bootstrap behavior is fixed as:
  - read the active row
  - if no active row exists, create the initial key inside the same database transaction
  - return the persisted active key from storage
- The initial key keeps the existing semantic shape: `archive-<UTC year>` plus a random 16-byte secret.
- `FreezeSnapshotService` and `FreezeSnapshotCryptoService` stay on the existing `activeKey()` / `findByKeyId(...)` boundary and must not absorb SQL/bootstrap responsibilities.

### Structured-State Initialization

- Register `SnapshotArchiveKeysTable` from `StructuredStateAppModule` alongside the existing structured-state tables.
- Treat that central registration list as an explicit composition-root choice for this task.
- Do not use this task to refactor table discovery or introduce dynamic registration.

### Documentation Impact

- `spec/Architecture.md` must move snapshot archive keys from filesystem runtime artifacts to SQLite-backed structured state.
- Runtime/help/documentation surfaces and related tests must stop presenting `mosa_snapshot_archive_keys.json` as a productive file.
- Any test cleanup that still deletes or expects `mosa_snapshot_archive_keys.json` should be updated in `sub003` unless `sub002` requires a narrow earlier change for compilation or behavioral correctness.

## Findings

### ARCH-1: Snapshot archive keys belong in structured state, not filesystem

- status: resolved
- title: Move productive snapshot archive key persistence into SQLite-backed structured state
- evidence or rationale:
  - The current productive wiring in `server/src/main/kotlin/straightway/mosa/identity/privacy/IdentityPrivacyAppModule.kt` binds `FileBackedFreezeSnapshotArchiveKeyStore` directly and derives `mosa_snapshot_archive_keys.json` from the working directory.
  - `spec/Architecture.md` currently lists "snapshot keys" under filesystem persistence, which conflicts with the task goal and with the existing repository principle that structured runtime state lives in `mosa_db.sqlite`.
  - Snapshot archive keys are server-only runtime data. They are not client/server contract types and must not move into `shared/`.
- recommended action:
  - Replace the productive file-backed implementation with a SQLite-backed store under the server module, backed by `StructuredStateContext`/`StructuredStateDatabase`.
  - Keep the existing domain-facing interfaces `FreezeSnapshotArchiveKeyStore` and `FreezeSnapshotActiveArchiveKeyProvider`; only the infrastructure implementation and DI wiring should change.
  - Register the new table from `StructuredStateAppModule` so snapshot archive keys become part of the same structured-state initialization path as other runtime tables.
- Developer Response: implemented in `sub002` with `SnapshotArchiveKeysTable`,
  `SqliteFreezeSnapshotArchiveKeyStore`, structured-state registration from
  `StructuredStateAppModule`, and productive DI rewiring in `IdentityPrivacyAppModule`.

### ARCH-2: Keep snapshot cryptology unchanged and isolate persistence concerns

- status: resolved
- title: Preserve snapshot service and crypto semantics while swapping persistence backend
- evidence or rationale:
  - `FreezeSnapshotService` and `FreezeSnapshotCryptoService` consume only `activeKey()` and `findByKeyId(...)`.
  - The architecture stays cleaner if key generation, active-key lookup, and historical-key lookup remain behind the existing snapshot-specific interfaces instead of leaking SQL or table details into services.
- recommended action:
  - Introduce a server-local SQLite implementation in the snapshot area or a tightly related structured-state package, but keep the public dependency surface unchanged for snapshot services.
  - The new store should continue to guarantee:
    - one active key returned by `activeKey()`
    - lookup of historical keys by `keyId`
    - lazy bootstrap of an initial key when no persisted key exists yet
  - Do not push Exposed types, SQL details, or structured-state table logic into `FreezeSnapshotService` or `FreezeSnapshotCryptoService`.
- Developer Response: implemented as planned. Snapshot services still consume only
  `FreezeSnapshotArchiveKeyStore` / `FreezeSnapshotActiveArchiveKeyProvider`;
  SQL and Exposed details remain isolated in `SqliteFreezeSnapshotArchiveKeyStore`.

### ARCH-3: Model the data for stable bootstrap and restart behavior

- status: resolved
- title: Use a schema that makes active-key resolution explicit
- evidence or rationale:
  - The file-backed format stores both `activeKeyId` and the full key list. The replacement schema must preserve the same operational guarantees after restart.
  - Restore paths depend on historical key lookup, while freeze paths depend on deterministic active-key lookup.
- recommended action:
  - Use a single table that stores all keys and an explicit active marker. The implementation must make "exactly one active key" easy to enforce and reason about.
  - Keep `keyId` unique and store the raw secret as persisted binary/text data local to the server database.
  - If bootstrap creates the first key on demand, do it inside structured-state persistence so concurrent reads cannot observe a partially initialized state.
- Developer Response: implemented in `sub002` with one snapshot-owned table containing
  `keyId`, `secret`, `isActive`, and `createdAt`, plus a single-active partial unique index.
  The store bootstraps only from an empty table, reuses persisted active and historical keys
  across restart, and fails loudly for malformed persisted state with zero or multiple active rows.

### ARCH-4: Update architecture and operational documentation in the same change

- status: resolved
- title: Remove productive references to `mosa_snapshot_archive_keys.json`
- evidence or rationale:
  - `spec/Architecture.md` still describes snapshot keys as filesystem-backed runtime state.
  - Tests such as `server/src/test/kotlin/straightway/mosa/MainArgumentsTest.kt` and `server/src/test/kotlin/straightway/mosa/AppModuleTest.kt` indicate current operational documentation and startup expectations still mention the JSON file.
- recommended action:
  - Update `spec/Architecture.md` so snapshot archive keys are described as structured state in SQLite, not filesystem state.
  - Update any runtime/help/documentation output and related tests so `mosa_snapshot_archive_keys.json` is no longer presented as a productive artifact.
  - If backward compatibility with an existing JSON file is desired, treat it as a one-time migration concern inside the server module, not as an enduring second persistence source.
- Developer Response: implemented in `sub003` by updating `spec/Architecture.md` so snapshot archive keys are now described as SQLite-backed structured state and no longer as filesystem runtime artifacts.

## Open Questions

- None for `sub002`. If the single-table `isActive` design proves technically insufficient during implementation, that should reopen architecture work explicitly instead of being redesigned ad hoc.

## Temporary Release Assumption

- For this task, no migration from `mosa_snapshot_archive_keys.json` is required.
- Reason: there is currently no released software in the field, so there is no supported upgrade path from productive JSON-backed archive keys to SQLite-backed archive keys.
- Consequence for implementation:
  - the new SQLite-backed store may ignore any legacy `mosa_snapshot_archive_keys.json`
  - no one-time import path is required
  - no dual-source bootstrap behavior is required
- This is a temporary pre-release assumption and should be removed once MOSA has released software that could carry legacy productive state.
