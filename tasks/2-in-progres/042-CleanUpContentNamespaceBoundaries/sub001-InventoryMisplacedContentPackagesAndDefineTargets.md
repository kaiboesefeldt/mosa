Create a concrete inventory of everything currently under `straightway.mosa.content` in `shared`, `server`, and `client`, then classify each item as either:

- stays in content
- moves to identity
- moves to snapshot
- moves to another existing responsibility namespace
- needs an explicit architecture decision because the ownership is unclear

The output of this subtask must leave behind an executable mapping from current package to target package, including the reason for each non-trivial move. The mapping should call out especially the current identity-moderation, identity-deletion, snapshot, blocked-identity, and structured-state slices.

Do not start moving code before this mapping is stable enough to avoid oscillating package moves.
