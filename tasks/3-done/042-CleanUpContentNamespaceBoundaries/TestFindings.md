TEST-1
title: The inventory does not yet capture downstream consumers for some planned namespace moves
status: resolved
evidence or rationale: |
  The current subtask requires an executable mapping before code moves start. The ownership and target-package
  decisions are now explicit, but the mapping still omits several downstream consumers that will have to move or at
  least update imports when the first implementation increment starts.

  Targeted repo scans:

  - `rg -n "ContentCapabilitiesService|ContentCapabilitiesResult|AdminIdentityModerationStatusService|IdentityModerationService|PrivacySnapshotPort|PodAccessFactory|JwtBuilder|AdminMessageHistoryService|AdminMessageInboxService|AdminMessagePublisher" client/src/jsMain/kotlin server/src/main/kotlin shared/src/commonMain/kotlin -g '*.kt'`
  - `rg -n "configureContentAppModule|AdminMessageCleanupJob|StructuredStateDatabase|StructuredStateContext|BlockedIdentityStore" server/src/main/kotlin -g '*.kt'`

  Those scans show non-trivial consumers outside the moved source files themselves, for example:

  - `client/legal/LegalImprintService.kt` and `client/legal/LegalPrivacyPolicyService.kt` consume `PodAccessFactory`
  - `client/pod/PodSessionProvider.kt` consumes both `PodAccessFactory` and `JwtBuilder`
  - `client/ui/*` files consume `PrivacySnapshotPort`, admin message services, and moderation services
  - `server/message/*`, `server/content/complaint/*`, and `server/content/snapshot/*` consume `StructuredStateContext`
    or `StructuredStateDatabase`
  - `server/network/NetworkServerPlugin.kt` imports nested exceptions from `IdentityModerationService`

  This is not yet a production bug, but it is a realistic failure mode for the next increment: the first package move
  can look locally complete while still leaving compile breaks or stale DI wiring in neighboring slices.
recommended action: |
  Extend `InventoryAndTargetMapping.md` or `ImplementationNotes.md` with an "impacted consumers" checklist for the
  highest-risk moves:

  - `StructuredStateContext` / `StructuredStateDatabase`
  - `BlockedIdentityStore`
  - `PodAccessFactory`
  - `JwtBuilder`
  - `PrivacySnapshotPort`
  - shared moderation/privacy DTOs

  The checklist does not need every import in the repo, but it should name the adjacent modules and files that must be
  revisited when the first code move starts.
Developer Response: |
  Resolved by adding an "Impacted Consumers Checklist" to `InventoryAndTargetMapping.md` and recording that addition in
  `ImplementationNotes.md`. The checklist now names the neighboring modules and files that must be revisited during the
  first code-moving increment.

Test command and outcome:

- No automated tests were added or run.
- Reason: current subtask changes only task-planning artifacts.
- The adversarial pass used targeted `rg` scans to challenge whether the mapping is implementation-ready.
- A final adversarial re-check after the impacted-consumers checklist did not produce a new meaningful gap in the
  planning artifacts. Remaining risk is execution risk during the later code move, not an additional unresolved
  planning defect in `sub001`.

Follow-up tester pass for `sub002`:

- No new adversarial finding after the code move and documentation update.
- Targeted commands:
  - `./gradlew :server:test --tests 'straightway.mosa.AppModuleTest' --tests 'straightway.mosa.identity.moderation.IdentityModerationServiceTest' --tests 'straightway.mosa.identity.moderation.IdentityModerationControllerTest' --tests 'straightway.mosa.identity.privacy.PrivacySnapshotControllerTest'`
  - `./gradlew :client:jsTest`
- Outcomes:
  - targeted server wiring and moderation/privacy tests passed
  - full client JS test suite passed
  - an attempted narrower command `./gradlew :client:jsTest --tests ...` was rejected by Gradle because `--tests` is
    not supported for that task in this setup, so the whole `:client:jsTest` task was used instead
- Remaining risk:
  - no failing test demonstrates a current behavior gap in `sub002`
  - remaining risk is ordinary maintenance risk from some test files still living under legacy filesystem paths

Follow-up tester pass for `sub003`:

- No new adversarial finding after the remaining client package move and residual test-path normalization.
- Challenged behaviors:
  - client DI and UI consumers still resolve the moved moderation/privacy services through the new namespaces
  - no stale imports remain to the old `straightway.mosa.client.content` owners for the moved moderation/privacy types
  - moved server test slices still execute from their normalized filesystem paths
- Targeted repo scans:
  - `rg -n "IdentityDeletionService|AdminIdentityModerationStatusService|IdentityModerationService|PrivacySnapshotPort|PrivacySnapshotService|PrivacySnapshotTransferService" client/src/jsTest/kotlin -g '*.kt'`
  - `rg -n "straightway\\.mosa\\.client\\.content\\.(AdminIdentityModerationStatusService|IdentityModerationService|IdentityDeletionService|PrivacySnapshotPort|PrivacySnapshotService|PrivacySnapshotTransferService)" . -g '*.kt'`
- Targeted commands:
  - `./gradlew :client:jsTest`
  - `./gradlew :server:test --tests 'straightway.mosa.identity.moderation.IdentityModerationControllerTest' --tests 'straightway.mosa.identity.privacy.PrivacySnapshotControllerTest' --tests 'straightway.mosa.identity.blocklist.SqliteBlockedIdentityStoreTest' --tests 'straightway.mosa.structuredstate.StructuredStateDatabaseTest'`
- Outcomes:
  - full client JS test suite passed
  - targeted server tests for the moved moderation/privacy/blocklist/structured-state slices passed
  - the stale client-owner scan produced no hits for the moved moderation/privacy client types
- Remaining risk:
  - no failing test demonstrates a current behavior gap in `sub003`
  - remaining risk is the final integrated namespace-closure pass in `sub004`, not an uncovered regression specific to the current client/path-cleanup increment

Follow-up tester pass for `sub004`:

- No new adversarial finding after the final integrated namespace-verification pass.
- Challenged behaviors:
  - the remaining `shared`, `server`, and `client` `content` packages do not still hide moved moderation/privacy/blocklist/structured-state owners
  - the final verification step did not silently skip a leftover old-package reference for the moved types
  - the closing pass did not rely on unnoticed dependency-file drift or package-boundary fallout in server runtime docs
- Targeted repo scans:
  - `find shared/src/commonMain/kotlin/straightway/mosa/content -type f | sort`
  - `find server/src/main/kotlin/straightway/mosa/content -type f | sort`
  - `find client/src/jsMain/kotlin/straightway/mosa/client/content -type f | sort`
  - `rg -n "straightway\\.mosa\\.content\\.(AdminIdentityModerationStatus|DeleteIdentityResult|FreezeSnapshotRestoreResult|IdentityModerationRequest)|straightway\\.mosa\\.client\\.content\\.(AdminIdentityModerationStatusService|IdentityModerationService|IdentityDeletionService|PrivacySnapshotPort|PrivacySnapshotService|PrivacySnapshotTransferPort|PrivacySnapshotTransferService)|straightway\\.mosa\\.content\\.(BlockedIdentityStore|BlockedIdentitiesTable|SqliteBlockedIdentityStore|StructuredStateContext|StructuredStateDatabase)" shared/src server/src client/src -g '*.kt'`
- Outcomes:
  - the remaining `content` inventories are consistent with content-owned responsibilities and complaint-adjacent flows
  - the residual old-package scan produced no hits for the moved moderation/privacy/blocklist/structured-state types
  - no hidden dependency-file change or server-doc fallout was evidenced in the closing pass
- Remaining risk:
  - no failing test demonstrates a current behavior gap in `sub004`
  - the only remaining work is the formal whole-task quality closure based on the accumulated evidence, not an uncovered test gap in the namespace cleanup itself
