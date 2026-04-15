Apply the first code-moving increment based on `InventoryAndTargetMapping.md`.

Scope of this subtask:

- move the shared moderation and privacy contracts to their approved target namespaces
- move the server-side moderation, privacy, snapshot, blocklist, and structured-state classes to their approved target namespaces
- split `ContentAppModule` along the approved ownership boundaries instead of moving it as one file
- update imports, package declarations, and DI wiring for all impacted server-side consumers named in the impacted-consumers checklist
- apply the minimal client-side import and DI updates that are structurally inseparable from the shared-contract moves

Required outcomes:

- `BlockedIdentityStore`, `BlockedIdentitiesTable`, and `SqliteBlockedIdentityStore` no longer live under `straightway.mosa.content`
- `StructuredStateContext` and `StructuredStateDatabase` no longer live under `straightway.mosa.content`
- identity moderation and identity privacy contracts no longer live in `shared/src/commonMain/kotlin/straightway/mosa/content`
- server snapshot internals no longer live under `straightway.mosa.content.snapshot`
- the old oversized `ContentAppModule` ownership leak is replaced by smaller module functions aligned with:
  - `straightway.mosa.content`
  - `straightway.mosa.identity.blocklist`
  - `straightway.mosa.identity.moderation`
  - `straightway.mosa.identity.privacy`
  - `straightway.mosa.structuredstate`

Constraints:

- keep HTTP routes, serialization payloads, and persistence behavior unchanged
- do not perform the full client package cleanup in this subtask; only make the client changes required so the shared-contract moves compile and the package graph stays internally consistent enough for the next increment
- use the impacted-consumers checklist as a required verification aid, not as optional guidance
- if the module split reveals a better neutral home for nested moderation exceptions, document the decision in `ImplementationNotes.md`

Verification expectations for this subtask:

- compile-level fallout from the moved shared and server packages is resolved
- all impacted server-side consumers from the checklist are revisited explicitly
- the client consumers that are structurally coupled to the moved shared contracts are revisited explicitly
- any remaining client package cleanup that is intentionally deferred is documented precisely for the next subtask instead of being silently left behind
