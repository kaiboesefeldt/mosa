TEST-1
title: The sub002 test plan does not yet require verification of repeated first-access bootstrap
status: resolved
evidence or rationale: |
  `ArchitectureNotes.md` fixes lazy bootstrap inside `SqliteFreezeSnapshotArchiveKeyStore` and says the first key
  must be created inside structured-state persistence so `activeKey()` cannot observe partially initialized state.
  The revised `ImplementationNotes.md` now covers malformed persisted state after bootstrap, but its expected test work
  still lists only:

  - active-key bootstrap
  - restart persistence
  - historical-key lookup
  - invalid persisted-secret length handling
  - malformed persisted state with zero active rows or multiple active rows

  That leaves an adversarial gap at the bootstrap seam itself: repeated first access to an empty database. A naive
  SQLite implementation can still race on "read active row -> insert new active row" and end up with two active rows
  or a uniqueness failure on the first cold start if two callers hit `activeKey()` concurrently or in quick sequence
  before the initial insert becomes visible. This is a realistic bug surface because the design moves bootstrap into
  the store and depends on transactional correctness rather than a process-wide file write.
recommended action: |
  Tighten the `sub002` plan so it explicitly requires proving one of these requirement-level behaviors:

  - repeated `activeKey()` calls against an empty database create exactly one persisted active key and then converge on
    that same key, or
  - concurrent first access is rejected deterministically in a documented way instead of leaving behind malformed
    persisted state

  The implementation tests should name that seam directly. A useful adversarial check would start from an empty SQLite
  file, trigger multiple first-access reads, and verify that persistence ends with exactly one active row and stable
  subsequent reads.
Developer Response: |
  Resolved in `ImplementationNotes.md` by tightening the fixed `sub002` test plan.
  The expected test work now explicitly requires:

  - repeated first-access bootstrap coverage against an empty database, and
  - verification that repeated `activeKey()` calls either converge on one persisted active key with stable subsequent
    reads or fail deterministically without leaving malformed persisted state behind

  That keeps the bootstrap seam as a first-class requirement-level behavior instead of an implicit implementation
  detail.

Targeted verification for `sub001`:

- Command: not run
- Outcome: `sub001` changed task-artifact planning files only, so no production/test code existed yet for a targeted
  automated test run. This finding comes from adversarial review of `ArchitectureNotes.md`, `ImplementationNotes.md`,
  and the current file-backed store/tests.

Latest tester review result:

- No new finding after the `TEST-1` resolution.
- I re-checked the revised `ImplementationNotes.md` against `ArchitectureNotes.md`, `CodeReview.md`, and the current
  file-backed store/tests. The current `sub001` plan now names the main requirement-level failure modes that were
  previously under-specified:
  malformed active-row state after bootstrap and repeated first-access bootstrap against an empty database.
- Residual risk is now implementation-only:
  `sub002` still has to prove those behaviors with automated tests against the SQLite-backed store, especially the
  cold-start bootstrap seam and deterministic failure behavior under malformed persisted state.

TEST-4
title: Concurrent cold-start bootstrap of SQLite snapshot keys did not produce a defect
status: resolved
evidence or rationale: |
  I added an exploratory test to `server/src/test/kotlin/straightway/mosa/identity/privacy/snapshot/SqliteFreezeSnapshotArchiveKeyStoreTest.kt`
  that synchronized two callers hitting `activeKey()` on an empty SQLite database at the same time, then asserted that
  both calls converged on the same persisted active key and left exactly one row behind. The test passed against the
  current implementation.

  This means the suspected bootstrap race is not currently demonstrated as a bug by the code under test. The remaining
  concern is only that the suite still lacks a direct stress test for higher-concurrency or repeated cold-start access.
recommended action: |
  No production change is warranted from this probe. Keep the existing cold-start and malformed-state tests, and if the
  task wants stronger assurance later, add a dedicated stress-style concurrency test instead of a one-off scenario.
Developer Response: |
  N/A. No defect was proven. The exploratory test was removed after it passed.

Targeted verification:

- Command: `./gradlew :server:test --tests 'straightway.mosa.identity.privacy.snapshot.SqliteFreezeSnapshotArchiveKeyStoreTest'`
- Outcome: passed

TEST-2
title: Malformed persisted SQLite archive keys leak raw storage exceptions during decrypt
status: resolved
evidence or rationale: |
  `sub002` keeps the existing snapshot-facing API contract while moving archive-key lookup into SQLite-backed
  structured state. That means restore/decrypt callers should still observe snapshot-level rejection semantics when
  persisted key material is corrupt, not raw persistence exceptions.

  I added an adversarial test in
  `server/src/test/kotlin/straightway/mosa/identity/privacy/snapshot/FreezeSnapshotCryptoServiceTest.kt` that:

  - encrypts a payload with a valid archive key
  - persists a SQLite row for the referenced `keyId` with malformed non-Base64 secret data
  - decrypts through `FreezeSnapshotCryptoService(SqliteFreezeSnapshotArchiveKeyStore(...))`

  The test expects `InvalidFreezeSnapshotException`, but the run fails because `SqliteFreezeSnapshotArchiveKeyStore.findByKeyId()`
  throws raw `IllegalArgumentException` while decoding the persisted secret. `FreezeSnapshotCryptoService` only maps
  missing keys (`null`) and `GeneralSecurityException`, so malformed persisted key material escapes as an internal
  storage exception.

  This breaks requirement-level behavior for restore/decrypt: a malformed persisted historical key should still be
  reported as an invalid snapshot/decrypt failure, not as an unwrapped repository/storage error.
recommended action: |
  Normalize malformed persisted key material at the archive-key-store / crypto boundary so decrypt/restore preserves
  snapshot-level failure semantics. One acceptable fix would be to make the SQLite store throw a snapshot-domain-safe
  exception for malformed persisted secrets, and another would be to have `FreezeSnapshotCryptoService` catch and map
  key lookup decoding failures consistently before returning control to callers.

  Keep the adversarial test and extend the reasoning to restore flows that depend on historical key lookup.
Developer Response: |
  Resolved in `SqliteFreezeSnapshotArchiveKeyStore` by mapping malformed persisted secrets to
  `InvalidFreezeSnapshotException("Invalid snapshot archive key state", cause)`.
  The store now preserves snapshot-level failure semantics for decrypt/restore callers instead of leaking raw
  base64 decoding exceptions.

Targeted verification for `sub002`:

- Command: `./gradlew :server:test --tests 'straightway.mosa.identity.privacy.snapshot.FreezeSnapshotCryptoServiceTest'`
- Outcome: failed on `malformed persisted archive key is rejected as invalid snapshot during decrypt`
  with `AssertionFailedError` caused by raw `IllegalArgumentException`, proving the SQLite-backed lookup path leaks
  malformed persisted-key errors instead of surfacing `InvalidFreezeSnapshotException`.
- Resolution: verified after the store fix with the same focused test class.

TEST-3
title: Serve doc still advertises the retired snapshot archive-key JSON file
status: resolved
evidence or rationale: |
  `sub003` is supposed to remove runtime/help/documentation surfaces that still present `mosa_snapshot_archive_keys.json`
  as a productive artifact. The current `server/src/main/resources/doc/serve/files.md` still lists that filename under
  "Snapshot archive keys", and `server/src/test/kotlin/straightway/mosa/MainArgumentsTest.kt` still expects the
  `serve --doc` output to print it.

  I added a narrow public-behavior test in
  `server/src/test/kotlin/straightway/mosa/MainArgumentsTest.kt` that asserts the doc output no longer contains
  `File name: mosa_snapshot_archive_keys.json`. The test fails against the current code, which proves the doc/help
  surface still contradicts the migration requirement and still presents the retired JSON file as productive storage.
recommended action: |
  Update the serve-doc surface and the associated assertion so the runtime documentation only names the SQLite-backed
  structured-state database and no longer lists `mosa_snapshot_archive_keys.json` as a storage file.

  Keep the failing test until the doc/help output is updated, then tighten the test to prove the retired filename is no
  longer emitted.
Developer Response: |
  Resolved in `server/src/main/resources/doc/serve/files.md`,
  `server/src/test/kotlin/straightway/mosa/MainArgumentsTest.kt`, and
  `server/src/test/kotlin/straightway/mosa/AppModuleTest.kt` by removing the retired JSON filename from the serve-doc
  output and stale cleanup reference.

Targeted verification for `sub003`:

- Command: `./gradlew :server:test --tests 'straightway.mosa.MainArgumentsTest'`
- Outcome: failed on `serve doc does not advertise snapshot archive key json as productive storage` with
  `AssertionFailedError` at `MainArgumentsTest.kt:245`.
- Resolution: rerun after the doc/help update; the test now passes and no longer checks the retired filename.
