CR-1
title: `BlockedIdentityStore` is assigned to the wrong owner and would reintroduce boundary leakage
status: resolved
evidence or rationale: |
  `InventoryAndTargetMapping.md` moves `BlockedIdentityStore`, `SqliteBlockedIdentityStore`, and
  `BlockedIdentitiesTable` into `straightway.mosa.identity.moderation`.
  That ownership does not match actual usage:

  - `server/src/main/kotlin/straightway/mosa/auth/AuthenticationController.kt`
  - `server/src/main/kotlin/straightway/mosa/auth/AuthAppModule.kt`
  - `server/src/main/kotlin/straightway/mosa/defense/DefenseRoutingPlugins.kt`
  - `server/src/main/kotlin/straightway/mosa/defense/blocklist/AuthenticatedIdentityBlockListPlugin.kt`
  - `server/src/main/kotlin/straightway/mosa/content/IdentityDeletionService.kt`

  These consumers are not all part of identity moderation. The store is used by authentication, request blocking,
  moderation, and identity deletion. Moving it under `identity.moderation` would force auth and defense to depend on a
  moderation-owned package, which recreates the same kind of accidental ownership leak the task is trying to remove.
recommended action: |
  Reclassify blocked-identity persistence as a cross-cutting identity blocklist capability rather than a moderation-only
  slice. The smallest credible correction is to place the store and table under a neutral owner that matches all
  consumers, for example a dedicated identity blocklist namespace or the chosen structured-state infrastructure
  boundary, and let moderation depend on that abstraction instead of owning it.
Developer Response: |
  Resolved in the task mapping by moving `BlockedIdentityStore`, `BlockedIdentitiesTable`, and
  `SqliteBlockedIdentityStore` to `straightway.mosa.identity.blocklist` and by updating the planned module split so
  moderation depends on that blocklist capability instead of owning it.

Residual risk:

- No further review findings in this planning increment, but the mapping still lacks proof through code changes. The
  next implementation step should re-check each proposed target namespace against actual import dependencies before the
  first package move lands.
- No additional code review findings after the blocklist ownership correction. Remaining risk is implementation risk,
  not a newly identified planning defect.
- No additional code review findings after the impacted-consumers checklist was added. The remaining uncertainty is
  whether the later code move follows this checklist completely, not whether the current planning artifact is missing a
  newly identified dependency.

CR-2
title: `spec/Architecture.md` still documents the pre-split `content` boundary and now contradicts the running code
status: resolved
evidence or rationale: |
  The implementation now wires dedicated modules for structured state, blocklist, identity privacy, and identity
  moderation in `server/src/main/kotlin/straightway/mosa/AppModule.kt:19-34`, with snapshot and deletion wiring living
  under `server/src/main/kotlin/straightway/mosa/identity/privacy/IdentityPrivacyAppModule.kt:26-74` and structured
  state living under `server/src/main/kotlin/straightway/mosa/structuredstate/StructuredStateAppModule.kt:10-16`.

  `spec/Architecture.md` still describes the old shape:

  - `spec/Architecture.md:68-77` lists only Auth, Content, Defense, Embedding, Legal, Network, Vector Store, and Web
    Client Delivery as server submodules.
  - `spec/Architecture.md:160-167` says `straightway.mosa.content` covers identity deletion and privacy snapshots.

  That is now false. The code has moved those responsibilities out of `content`, so the architecture spec no longer
  matches the system that `./gradlew check` just validated.
recommended action: |
  Update `spec/Architecture.md` in the same task before considering the namespace cleanup done. The smallest credible
  fix is:

  - list the new server slices introduced by the split
  - narrow the `content` section to content-owned responsibilities only
  - add a short note that structured state is a separate infrastructure slice and that identity privacy/moderation own
    deletion, snapshots, and moderation flows
Developer Response: |
  Resolved by updating `spec/Architecture.md` to match the shipped module split:

  - the server submodule list now includes identity blocklist, identity moderation, identity privacy, and structured
    state
  - the `content` section no longer claims identity deletion or privacy snapshot ownership
  - the spec now explains the identity slice split and the dedicated `straightway.mosa.structuredstate` owner

Residual risk:

- I did not find an additional code-level defect in the package move itself after the final green `./gradlew check`.
- Remaining risk is mostly maintenance risk: several test files still live under old filesystem paths although their
  Kotlin packages were updated, which is confusing but not a proven behavior bug.

Follow-up review result:

- No additional review finding after the architecture spec update.
- The current remaining risk is documentation and maintainability risk, not a newly identified behavior bug:
  old filesystem paths in some test files are still mildly misleading even though the Kotlin packages and DI wiring are
  now consistent.

Follow-up review result for `sub003`:

- No additional review finding after the remaining client package move and residual test-path cleanup.
- I re-checked the client DI and UI consumers against the moved moderation/privacy services, verified that the shared
  DTO imports now point to `straightway.mosa.identity.moderation` and `straightway.mosa.identity.privacy`, and found no
  leftover client references to the old `straightway.mosa.client.content` owners for those types.
- Remaining risk is normal maintenance risk only: a few content-owned tests still live under legacy content-oriented
  directories by design, while the final integrated verification and whole-task closure still belong to `sub004`.

Follow-up review result for `sub004`:

- No additional review finding after the final integrated namespace-verification pass.
- I re-checked the remaining `shared`, `server`, and `client` `content` packages against the task acceptance criteria,
  verified that the documented old-package scans for moderation/privacy/blocklist/structured-state leftovers are clean,
  and did not find a new boundary leak or behavior risk hidden by the final verification step.
- Remaining risk is only normal closure risk: whole-task completion still depends on the final quality pass confirming
  that the accumulated evidence across `sub002` to `sub004` is sufficient for task closure.
