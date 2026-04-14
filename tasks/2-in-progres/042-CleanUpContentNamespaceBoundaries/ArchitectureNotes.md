Initial boundary guidance:

- `straightway.mosa.content` should keep content publication, content querying, content fetch/export, content signatures, and content deletion of posts.
- Identity moderation and identity deletion should be evaluated as `straightway.mosa.identity` concerns unless a narrower existing namespace is already a better fit.
- Privacy snapshot contracts should align with the existing snapshot slice instead of remaining in the root content package.
- `StructuredStateDatabase` and `StructuredStateContext` should not remain under `straightway.mosa.content` if they are shared storage infrastructure for complaints, messages, blocking, or snapshots.
- `spec/Architecture.md` currently states that content covers identity deletion and privacy snapshots. That description must be tightened when the implementation boundary is corrected.

Architectural intent:

- one responsibility per namespace
- shared contracts only when they are real client-server protocol types
- package names should explain ownership without reading implementation history
- adjacent concerns may collaborate with content, but collaboration is not ownership
