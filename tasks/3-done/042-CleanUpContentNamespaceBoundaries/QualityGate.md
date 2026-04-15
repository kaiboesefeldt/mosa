QA-1
title: `sub002` is ready to close
status: resolved
evidence or rationale: |
  The current subtask required the first real code-moving increment for the namespace cleanup. The required outcomes are
  now evidenced in code and task artifacts:

  - shared moderation and privacy contracts were moved out of `straightway.mosa.content`
  - server moderation, privacy, snapshot, blocklist, and structured-state classes were moved to their approved owners
  - `ContentAppModule` was split and reduced to content-owned wiring
  - `spec/Architecture.md` was updated so the architecture description matches the shipped module split

  Supporting evidence exists in:

  - `ImplementationNotes.md`
  - `CodeReview.md`
  - `TestFindings.md`
  - `spec/Architecture.md`

  The current reviewer and tester passes do not leave an open unresolved finding against `sub002`.
recommended action: |
  Close `sub002-MoveSharedAndServerNamespacesAndSplitModules.md` and continue the task with the next increment for the
  remaining client-side package cleanup and residual path cleanup.
Developer Response: |
  Quality accepted `sub002` and closed it.

QA-2
title: Required final verification evidence exists for the code-changing increment
status: resolved
evidence or rationale: |
  This increment changed production and test code, so the repository quality gate requires a final `./gradlew check`
  run documented in `ImplementationNotes.md`.

  `ImplementationNotes.md` records:

  - `./gradlew compileKotlinJvm compileKotlinJs --continue`: passed
  - `./gradlew compileTestKotlinJvm compileTestKotlinJs --continue`: passed
  - `./gradlew check`: passed

  Additional targeted evidence exists in `TestFindings.md`:

  - `./gradlew :server:test --tests 'straightway.mosa.AppModuleTest' --tests 'straightway.mosa.identity.moderation.IdentityModerationServiceTest' --tests 'straightway.mosa.identity.moderation.IdentityModerationControllerTest' --tests 'straightway.mosa.identity.privacy.PrivacySnapshotControllerTest'`
  - `./gradlew :client:jsTest`
recommended action: |
  No additional mandatory verification is required for closing this subtask.
Developer Response: |
  Verified and accepted.

QA-3
title: Residual legacy test file paths are acceptable to defer
status: resolved
evidence or rationale: |
  Some test files still live under legacy filesystem paths such as `server/src/test/kotlin/straightway/mosa/content/...`
  even though their Kotlin packages now point at the new owners. Review and tester passes both call this out as a
  maintenance risk, but not a behavior bug.

  The subtask explicitly allowed only the minimal client fallout and the required server-side namespace cleanup. Fully
  normalizing residual file layout is a follow-up cleanup concern, not a blocker for closing this increment.
recommended action: |
  Carry the residual file-path normalization into a later increment instead of reopening `sub002`.
Developer Response: |
  Accepted as intentional deferral.

Whole-task review after closing the current subtask:

- The overall task remains open.
- `done-sub001-InventoryMisplacedContentPackagesAndDefineTargets.md` and the now-closed `sub002` cover planning plus
  the first code-moving increment only.
- `DecompositionPlan.md` still implies later work for broader client package cleanup and residual namespace/path
  normalization.

QA-4
title: `sub003` is ready to close
status: resolved
evidence or rationale: |
  The current subtask required the deferred client-side namespace cleanup plus normalization of the highest-value
  misleading test paths. The expected outcomes are now evidenced in code and coordination artifacts:

  - the remaining client moderation and privacy services moved from `straightway.mosa.client.content` to
    `straightway.mosa.client.identity.moderation` and `straightway.mosa.client.identity.privacy`
  - client DI and UI consumers were updated to the new client identity namespaces
  - the highest-value misleading server test files now live under filesystem paths that match their Kotlin packages
  - the moved client privacy snapshot test now also lives under a matching identity-privacy test path

  Supporting evidence exists in:

  - `ImplementationNotes.md`
  - `CodeReview.md`
  - `TestFindings.md`

  The current reviewer and tester passes do not leave an unresolved finding against `sub003`.
recommended action: |
  Close `sub003-MigrateRemainingClientPackagesAndNormalizeResidualPaths.md` and continue with `sub004` for the final
  integrated verification and task closure.
Developer Response: |
  Quality accepted `sub003` and closed it.

QA-5
title: Required verification evidence exists for the code-changing `sub003` increment
status: resolved
evidence or rationale: |
  `sub003` changed production and test code, so the repository quality gate requires a documented final
  `./gradlew check` run in `ImplementationNotes.md`.

  `ImplementationNotes.md` records:

  - targeted scans for stale client-owner references and misleading moved server test paths: passed
  - `./gradlew compileTestKotlinJvm compileTestKotlinJs --continue`: passed
  - `./gradlew :server:test --tests 'straightway.mosa.identity.moderation.IdentityModerationServiceTest' --tests 'straightway.mosa.identity.moderation.IdentityModerationControllerTest' --tests 'straightway.mosa.identity.privacy.PrivacySnapshotControllerTest' --tests 'straightway.mosa.identity.blocklist.SqliteBlockedIdentityStoreTest' --tests 'straightway.mosa.structuredstate.StructuredStateDatabaseTest'`: passed
  - `./gradlew :client:jsTest`: passed
  - `./gradlew check`: passed

  `TestFindings.md` independently confirms the targeted client/server verification for the moved slices, and
  `CodeReview.md` does not report an unresolved defect that would block closure of this increment.
recommended action: |
  No additional mandatory verification is required for closing `sub003`. The remaining integrated end-to-end review
  belongs to `sub004`.
Developer Response: |
  Verified and accepted.

QA-6
title: `sub004` is ready to close
status: resolved
evidence or rationale: |
  The current subtask required the final integrated verification pass for the namespace cleanup task. The expected
  outcomes are now evidenced in the coordination artifacts:

  - the remaining `shared`, `server`, and `client` `content` packages were explicitly inventoried and judged to be
    content-owned
  - residual old-package scans for moved moderation/privacy/blocklist/snapshot/structured-state types are clean
  - `spec/Architecture.md` already matches the implemented split
  - `server/src/main/resources/doc/serve` was checked and found unaffected
  - Gradle dependency/version files were checked and show no unnoticed third-party dependency addition

  Supporting evidence exists in:

  - `ImplementationNotes.md`
  - `CodeReview.md`
  - `TestFindings.md`
  - `spec/Architecture.md`

  The current reviewer and tester passes do not leave an unresolved finding against `sub004`.
recommended action: |
  Close `sub004-VerifyIntegratedNamespaceCleanupAndCloseTask.md` and perform the final whole-task review for task
  `042-CleanUpContentNamespaceBoundaries`.
Developer Response: |
  Quality accepted `sub004` and closed it.

QA-7
title: The whole task is complete and ready to be proposed for status move
status: resolved
evidence or rationale: |
  All planned subtasks are now closed:

  - `done-sub001-InventoryMisplacedContentPackagesAndDefineTargets.md`
  - `done-sub002-MoveSharedAndServerNamespacesAndSplitModules.md`
  - `done-sub003-MigrateRemainingClientPackagesAndNormalizeResidualPaths.md`
  - `done-sub004-VerifyIntegratedNamespaceCleanupAndCloseTask.md`

  The accumulated evidence across the task shows:

  - planning and ownership decisions were made explicit before code moves
  - shared, server, and client namespace cleanup was implemented in coherent increments
  - `spec/Architecture.md` was updated to reflect the shipped module boundaries
  - `server/src/main/resources/doc/serve` was reviewed in the final subtask and found unaffected
  - no new third-party dependency change was introduced as part of the cleanup
  - the required `./gradlew check` run for the code-changing increments is documented as green in
    `ImplementationNotes.md`
  - the latest reviewer and tester passes report no unresolved finding

  Residual risk is ordinary maintenance risk only: some intentionally content-owned tests still live under legacy
  content-oriented filesystem paths, but this is documented as non-blocking and no behavior defect has been shown.
recommended action: |
  The task is ready to be proposed for moving from `tasks/2-in-progres/042-CleanUpContentNamespaceBoundaries` to
  `tasks/3-done/042-CleanUpContentNamespaceBoundaries` after human approval, per task-board rules.
Developer Response: |
  Whole-task quality review completed. No additional implementation, test, or documentation work is required inside
  this task.
