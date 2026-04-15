Apply the remaining namespace cleanup after `sub002`.

Scope of this subtask:

- complete the client-side package cleanup that was intentionally deferred from `sub002`
- remove residual false ownership in client package names where identity privacy or identity moderation behavior still
  looks content-owned only because of package placement or stale type names
- normalize remaining test file paths whose Kotlin packages already moved to new owners but whose filesystem locations
  still preserve the old misleading `content` layout
- update task documentation if a residual client or test-layout exception must stay deferred for a concrete reason

Required outcomes:

- no client package or file naming still implies that identity moderation or privacy behavior is owned by
  `straightway.mosa.client.content` when a more accurate owner exists
- the intentionally deferred client cleanup from `sub002` is either implemented or documented precisely as a further
  explicit deferral
- the highest-value legacy server test file paths are aligned with their Kotlin packages, especially for:
  - identity moderation tests
  - identity privacy tests
  - blocklist tests
  - structured-state tests
  - privacy snapshot tests
- no stale task note still describes the client cleanup as merely "later" without tying it to a concrete outcome

Constraints:

- keep runtime behavior, HTTP routes, payloads, and persistence behavior unchanged
- do not re-open already settled ownership decisions unless a new architecture finding proves them wrong
- avoid purely cosmetic churn outside the namespace and path cleanup scope
- when moving test files, preserve test behavior and coverage rather than rewriting tests wholesale

Verification expectations for this subtask:

- compile-level fallout from the remaining client and test-path moves is resolved
- `ImplementationNotes.md` records exactly which deferred cleanup from `sub002` was finished here
- any remaining exceptions are explicit and narrow enough for a final verification subtask
