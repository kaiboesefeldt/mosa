Initial boundary guidance:

- `straightway.mosa.content` should keep content publication, content querying, content fetch/export, content signatures, and content deletion of posts.
- Identity moderation is expected to be an identity concern unless the inventory uncovers a stronger existing owner.
- Blocked-identity state is not owned by identity moderation. It is a neutral identity blocklist capability used by moderation, auth, defense, and identity privacy flows.
- Identity deletion must be treated separately from identity moderation. It is a lifecycle orchestration flow that touches content, complaints, admin messages, and snapshots, so it must not be moved under `straightway.mosa.identity` by default just because it starts from a GID.
- Privacy snapshot contracts should align with the existing snapshot slice instead of remaining in the root content package.
- `StructuredStateDatabase`, `StructuredStateContext`, and related storage helpers should not remain under `straightway.mosa.content` if they are shared storage infrastructure for complaints, messages, blocking, or snapshots.
- If structured-state classes are truly cross-feature persistence infrastructure, they should be consolidated under one dedicated server-side infrastructure boundary instead of being redistributed piecemeal into feature packages.
- `spec/Architecture.md` currently states that content covers identity deletion and privacy snapshots. That description must be tightened when the implementation boundary is corrected.
- Shared protocol packages should mirror stable cross-module domain boundaries. Client and server implementation packages do not need perfect symmetry with each other, but they should depend on shared packages whose ownership is stable and explicit.
- When a shared-contract move forces immediate import or DI fallout in the client, that fallout belongs to the same implementation increment as the shared move. Do not pretend those client adaptations are a separate structural step if the code cannot compile without them.

Architectural intent:

- one responsibility per namespace
- shared contracts only when they are real client-server protocol types
- package names should explain ownership without reading implementation history
- adjacent concerns may collaborate with content, but collaboration is not ownership
- do not let cross-cutting lifecycle orchestration masquerade as a feature-local package only because one feature triggers it
