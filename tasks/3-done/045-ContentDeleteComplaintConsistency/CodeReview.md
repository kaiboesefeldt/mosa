# Code Review

CR-1
- Status: closed
- Severity: medium
- Location: `server/src/main/kotlin/straightway/mosa/content/ContentDeleteService.kt:95-107`
- Issue: The rollback path is not exception-safe. `restorePosts()` can throw while rebuilding deleted content because it re-embeds each post body and performs a second `writeAccess` transaction inside the catch block.
- Why this is risky: the code is trying to preserve the system when deletion fails, but a failure during compensation can now mask the original exception and leave the delete only partially rolled back. That defeats the stated atomicity goal and makes the final state harder to reason about.
- Recommended action: make rollback best-effort but non-throwing, or restore from already-saved vectors/transaction state so compensation cannot fail for avoidable reasons. At minimum, preserve the original exception if rollback also fails.
- Developer Response: Resolved by making rollback best-effort in `rollbackDeletion()`, catching failures from both post restoration and complaint restoration, and preserving the original delete exception while attaching rollback failures as suppressed exceptions.

CR-2
- Status: closed
- Severity: medium
- Location: `server/src/main/kotlin/straightway/mosa/content/ContentDeleteService.kt:78-93`
- Issue: Reply traversal now recurses through descendants without any visited-set protection.
- Why this is risky: a malformed or cyclic `ROOT_ID_KEY` graph can now drive unbounded recursion during both `delete()` and `deleteAllForUser()`, which can hang or stack overflow the deletion path. The previous implementation only walked one reply level, so this is a new failure mode introduced by the deeper traversal.
- Recommended action: thread a visited set through the recursive traversal and stop revisiting IDs already seen.
- Developer Response: Resolved by threading a `visitedRoots` set through `collectReplyIds()` and stopping recursion when a reply root has already been visited. Added regressions for both descendant traversal and cycle handling.

Residual risk:
- The new tests cover the happy-path descendant traversal and one rollback case, but they do not exercise rollback-failure handling or cyclic reply graphs.
