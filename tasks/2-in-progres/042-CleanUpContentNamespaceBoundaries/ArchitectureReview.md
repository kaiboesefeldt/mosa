AR-1
title: Current subtask ordering makes the task operationally inconsistent
status: superseded
evidence or rationale: |
  This finding was based on an incorrect assumption about task-board coordination. The authoritative rule is now
  documented as: the current subtask is the open subtask with the lowest `NNN`, not the highest.

  With that rule, opening `sub001` through later subtasks at the same time does not make the verification increment
  current prematurely.
recommended action: |
  No architectural correction is required from this finding itself. Apply the lowest-open-subtask rule consistently in
  task documentation.
Developer Response: |
  Superseded after the coordination rule was corrected to "lowest open `subNNN` is current".

AR-2
title: The target owner for structured-state infrastructure is still undefined
status: resolved
evidence or rationale: |
  The task originally questioned whether `StructuredStateDatabase`, `StructuredStateContext`, and related classes
  belong under `straightway.mosa.content` without stating what boundary should own them if they move. The revised
  `Description.md`, `ArchitectureNotes.md`, and `sub001` now require an explicit ownership decision before any package
  move: either a dedicated server-side infrastructure owner or a proven feature owner.
recommended action: |
  During `sub001`, do not produce only a destination list. Produce the ownership decision that justifies that list.
Developer Response: |
  Resolved by requiring an explicit ownership decision for structured-state infrastructure before package moves start.

AR-3
title: Identity deletion is treated as an obvious identity concern without proving the boundary
status: resolved
evidence or rationale: |
  The task originally leaned toward `straightway.mosa.identity` for identity deletion even though the flow spans
  content, complaints, admin messages, and snapshots. The revised task now separates identity moderation from identity
  deletion lifecycle and explicitly forbids assuming that identity deletion belongs to `identity` before the inventory
  proves it.
recommended action: |
  Keep identity deletion framed as an ownership question until the inventory produces the supporting evidence.
Developer Response: |
  Resolved by rewriting the task and architecture notes to treat identity deletion as lifecycle orchestration unless
  proven otherwise.

AR-4
title: Shared contract movement lacks a cross-module namespace policy
status: resolved
evidence or rationale: |
  The task originally had no rule describing how `shared`, `server`, and `client` package structures should relate.
  `ArchitectureNotes.md` now states that shared protocol packages should mirror stable cross-module domain boundaries,
  while client and server may keep distinct local implementation packages as long as they depend on shared packages
  with explicit ownership.
recommended action: |
  Use that rule as the package-placement test during the inventory and later moves.
Developer Response: |
  Resolved by adding an explicit cross-module namespace policy to `ArchitectureNotes.md`.

No further architecture findings at this stage.

Residual risks and open assumptions:

- The task is now implementation-ready for the inventory step only. It is not yet evidence that any specific target
  namespace choice is correct; that still depends on the `sub001` inventory and ownership decisions.
- The eventual implementation can still fail structurally if it chooses package destinations that optimize local naming
  convenience over explicit ownership, especially for lifecycle orchestration and shared persistence infrastructure.
- `spec/Architecture.md` still documents the old boundary today. That is acceptable for the planning state, but the
  implementation must update it in the same change that applies the boundary cleanup.

AR-5
title: `sub002` splits shared-contract moves from client fallout too aggressively
status: resolved
evidence or rationale: |
  `sub002-MoveSharedAndServerNamespacesAndSplitModules.md` scopes the first code-moving increment to shared and server
  code and says client code should not move except where shared-contract compilation requires temporary import updates.
  That boundary is weaker than it sounds, because the planned shared moves immediately affect real client dependencies:

  - `client/pod/PodAccess.kt` consumes `AdminIdentityModerationStatus`, `IdentityModerationRequest`,
    `DeleteIdentityResult`, and `FreezeSnapshotRestoreResult`
  - `client/content/AdminIdentityModerationStatusService.kt`
  - `client/content/IdentityModerationService.kt`
  - `client/content/IdentityDeletionService.kt`
  - `client/content/PrivacySnapshotPort.kt`
  - `client/content/PrivacySnapshotService.kt`
  - `client/AppModule.kt`
  - multiple UI entry points through those services

  This means the proposed increment is not a clean "shared + server only" slice. In practice it already includes a
  client adaptation slice, even if that adaptation is framed as temporary import fallout. Leaving that implicit makes
  the subtask harder to estimate and easier to leave in a half-migrated state where shared ownership moved but client
  package boundaries still encode the old ownership model.
recommended action: |
  Tighten the subtask boundary one step further. Choose one of these two explicit shapes before implementation starts:

  - either keep `sub002` truly server-side by postponing shared contract moves until the client migration subtask
  - or allow `sub002` to include the minimal client adaptations that are structurally inseparable from the shared moves,
    and say so explicitly in the subtask text

  The smallest correction is usually the second option: keep full client package cleanup for the next subtask, but
  explicitly include all required client import and DI updates caused by shared contract moves in `sub002`.
Developer Response: |
  Resolved by tightening `sub002` to include the minimal client-side import and DI changes that are inseparable from
  the shared-contract moves, while still deferring the broader client package cleanup to the next increment.

No further architecture findings after the `sub002` boundary correction.

AR-6
title: Opening `sub003` and `sub004` together re-breaks executable subtask ordering
status: superseded
evidence or rationale: |
  This finding was based on the same incorrect assumption as `AR-1`.

  `tasks/Coordination.md` now defines the current executable increment as the open subtask with the lowest `NNN`.

  The task directory now contains both:

  - `sub003-MigrateRemainingClientPackagesAndNormalizeResidualPaths.md`
  - `sub004-VerifyIntegratedNamespaceCleanupAndCloseTask.md`

  Under the corrected rule, `sub003` is the active subtask and `sub004` remains a later increment. The split is
  therefore implementation-ready as written.
recommended action: |
  No structural correction is required. Keep the documented rule consistent across task coordination files.
Developer Response: |
  Superseded after the coordination rule was corrected to "lowest open `subNNN` is current".

Residual architectural risk:

- The remaining split between `sub003` and `sub004` is structurally coherent: one increment for residual cleanup, one
  for final verification and closure.
