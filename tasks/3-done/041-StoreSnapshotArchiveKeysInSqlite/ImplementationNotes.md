## Current Increment

- Subtask: `sub002-ImplementSqliteBackedSnapshotArchiveKeyStore`
- Goal: move productive snapshot archive-key persistence into SQLite-backed structured state without changing snapshot-facing behavior

## Work Done

- added `SnapshotArchiveKeysTable` in
  `server/src/main/kotlin/straightway/mosa/identity/privacy/snapshot/`
  with `keyId`, `secret`, `isActive`, and `createdAt`
- added `SqliteFreezeSnapshotArchiveKeyStore` behind the existing snapshot-facing interfaces
  and kept snapshot cryptology/services free of SQL details
- implemented lazy bootstrap inside the SQLite-backed store so the first active key is created
  only from an empty table and reused across fresh store instances
- enforced the single-active-key storage rule with a partial unique index created from
  `SnapshotArchiveKeysTable.ensureIndexes()`
- made malformed persisted state fail loudly:
  zero active rows after bootstrap and multiple active rows both reject `activeKey()`
- rewired productive DI in
  `server/src/main/kotlin/straightway/mosa/identity/privacy/IdentityPrivacyAppModule.kt`
  from the file-backed store to `SqliteFreezeSnapshotArchiveKeyStore`
- registered the new snapshot table in
  `server/src/main/kotlin/straightway/mosa/structuredstate/StructuredStateAppModule.kt`
  so snapshot archive keys initialize with the structured-state database
- replaced the old file-backed store test with
  `server/src/test/kotlin/straightway/mosa/identity/privacy/snapshot/SqliteFreezeSnapshotArchiveKeyStoreTest.kt`
  covering bootstrap, repeated first access, restart persistence, historical lookup,
  invalid secret length, zero active rows, and multiple active rows
- updated
  `server/src/test/kotlin/straightway/mosa/identity/privacy/snapshot/FreezeSnapshotRestoreServiceTest.kt`
  so the historical-key restore/rotation scenario uses the SQLite-backed store

## Implemented Outcomes

- productive runtime wiring no longer depends on `FileBackedFreezeSnapshotArchiveKeyStore`
- snapshot services still consume only `FreezeSnapshotArchiveKeyStore` and
  `FreezeSnapshotActiveArchiveKeyProvider`
- active-key bootstrap, restart persistence, and historical lookup are now exercised against SQLite
- repeated first access from fresh store instances converges on one persisted active key
- malformed persisted state is rejected deterministically instead of selecting an arbitrary row
- the pre-release no-migration assumption remains in place:
  legacy `mosa_snapshot_archive_keys.json` is ignored, and no import/dual-read path was added

## Deferred To `sub003`

- Update `spec/Architecture.md` to describe snapshot archive keys as SQLite-backed structured state.
- Update runtime/help output and related tests so `mosa_snapshot_archive_keys.json` no longer appears as a productive
  runtime artifact.
- Perform a residual-reference scan for `mosa_snapshot_archive_keys.json`.

## Verification

- `./gradlew :server:test --tests 'straightway.mosa.identity.privacy.snapshot.SqliteFreezeSnapshotArchiveKeyStoreTest' --tests 'straightway.mosa.identity.privacy.snapshot.FreezeSnapshotRestoreServiceTest'`
  Result: passed
- `./gradlew check`
  Result: failed once on `:server:lintKotlinMain` because `SnapshotArchiveKeysTable.singleActiveIndexName`
  violated the repository constant-naming rule
- `./gradlew check`
  Result: passed after renaming the constant to `SINGLE_ACTIVE_INDEX_NAME`
- `./gradlew :server:test --tests 'straightway.mosa.identity.privacy.snapshot.SqliteFreezeSnapshotArchiveKeyStoreTest' --tests 'straightway.mosa.identity.privacy.snapshot.FreezeSnapshotCryptoServiceTest'`
  Result: passed after normalizing malformed persisted archive secrets to `InvalidFreezeSnapshotException`
- `./gradlew check`
  Result: passed
- `./gradlew :server:test --tests 'straightway.mosa.MainArgumentsTest' --tests 'straightway.mosa.AppModuleTest'`
  Result: passed after removing the retired snapshot archive-key JSON reference from runtime docs and test cleanup
- `./gradlew check`
  Result: passed

## Unresolved

- no open developer-side implementation question remains inside `sub002`
- `sub003` still owns the documentation/help cleanup and the residual-reference scan for
  `mosa_snapshot_archive_keys.json`

## Sub003 Work

- updated `spec/Architecture.md` so snapshot archive keys are described as SQLite-backed structured state rather than
  filesystem runtime artifacts
- kept the existing runtime/help output aligned with the SQLite-backed store and preserved the no-migration
  assumption for legacy `mosa_snapshot_archive_keys.json`

## Sub003 Verification

- `rg -n "mosa_snapshot_archive_keys\\.json" spec server/src/main server/src/test -g '!**/build/**'`
  Result: no matches
- `./gradlew check`
  Result: passed
