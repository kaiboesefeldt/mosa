# Test Findings

## TEST-5

- Title: `deleteAllForUser()` rollback failure now has explicit coverage
- Status: closed
- Evidence or rationale: Added a regression in `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` that forces the delete-all path to fail during post removal and then fail again while restoring content. The test verifies the original `delete failed` exception is preserved and the rollback failure is attached as suppressed, which was not covered by the existing delete-only regression.
- Recommended action: Keep this regression so the delete-all rollback path remains protected.
- Developer Response:
- Command: `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$DeleteAll.preserves original delete failure when rollback restore also fails'`
- Outcome: passed.

## TEST-4

- Title: Reply deletion does not restore content after a transactional remove failure
- Status: closed
- Evidence or rationale: Added a regression in `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` that uses a minimal vector-store fake which removes the reply and then throws from `writeAccess()`. The test fails at the assertion that the reply is still present after `ContentService.delete()` fails, showing the current flow can leave content deleted when complaint cleanup or vector-store deletion fails mid-transaction.
- Recommended action: Add compensation for content removal, not just complaint restoration, so a failed deletion leaves the post tree unchanged.
- Developer Response: Rechecked the regression with `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.keeps content when reply deletion fails after remove'`; it now passes against the current implementation, so no code change was required.
- Command: `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.keeps content when reply deletion fails after remove'`
- Outcome: original report failed at `ContentServiceTest.kt:1382`; rerun passed.

## TEST-3

- Title: Root-post deletion does not remove reply descendants
- Status: closed
- Evidence or rationale: Added a regression in `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` for a root post whose direct reply has its own descendant. `ContentService.delete()` only traverses the root's direct reply pages; it never recurses into replies of replies, so the new test fails at `transaction.remove(descendantId)`.
- Recommended action: Extend root-post deletion to traverse reply descendants recursively or otherwise ensure all descendants are collected before deletion.
- Developer Response: Resolved by making `ContentDeleteService.collectReplyIds()` recurse through reply descendants and updating the regression test to stub the descendant query.
- Command: `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.removes descendant replies when deleting root post'`
- Outcome: passed.

## TEST-2

- Title: Root-post reply pagination is covered by regression test
- Status: closed
- Evidence or rationale: Ran `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.removes replies when deleting root post'` against the current reply-pagination subtask. The test passed and verified traversal across more than one reply page, including a `queryOnId` continuation and the final empty page.
- Recommended action: No action for the reply-pagination subtask. The remaining open risk is in the separate complaint-rollback subtask, not this pagination path.
- Developer Response:
- Command: `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.removes replies when deleting root post'`
- Outcome: passed.

## TEST-1

- Title: Complaint cleanup failure is swallowed during identity deletion
- Status: closed
- Evidence or rationale: The regression now injects a `deleteForPost()` failure into the `ContentService` complaint-store dependency that `deleteAllForUser()` actually uses. `DELETE /v1/identity` returns `500 Internal Server Error`, and the fetched root post plus reply remain present.
- Recommended action: None.
- Developer Response: Resolved by wiring the failing complaint store into the content service used by identity deletion and restoring the reply assertion.
- Command: `./gradlew :server:test --tests 'straightway.mosa.content.complaint.PostComplaintIntegrationTest.identity deletion fails and leaves content intact when complaint cleanup fails'`
- Outcome: passed.
# Test Findings

## TEST-1

- title: `deleteAllForUser()` skips descendants of user-authored replies
- status: closed
- evidence or rationale: The regression in `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` now passes after `collectAllUserPostIds()` was updated to expand every user-authored post, including replies with descendants.
- recommended action: None.
- Developer Response: Resolved by expanding descendant traversal for all deleted user-authored posts and updating the regression test to cover the broader traversal.
- targeted test command: `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$DeleteAll.removes descendants of user replies when deleting all content for user'`
- outcome: passed.
