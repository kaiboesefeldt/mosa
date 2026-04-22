# Implementation Notes

## sub001 - Delete all replies for root posts

- Updated `server/src/main/kotlin/straightway/mosa/content/ContentDeleteService.kt` so root-post deletion follows `queryOnId` across all reply pages instead of stopping after the first page.
- Extended `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` to cover a root post with replies on multiple pages.

## sub002 - Complaint cleanup failure visibility

- Adjusted `ContentDeleteService.deleteAllForUser()` and `deleteRepliesForRoot()` to preload complaint rows before deleting posts and restore them if cleanup fails.
- Added an integration regression in `server/src/test/kotlin/straightway/mosa/content/complaint/PostComplaintIntegrationTest.kt` for identity deletion failing when complaint cleanup on a deleted root post throws, and kept the reply fetch assertion so the full tree remains covered.

## sub002 - Descendants of user replies

- Updated `ContentDeleteService.collectAllUserPostIds()` so every user-authored post expands its reply tree, not just root posts.
- Tightened `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` to stub and verify descendant traversal for replies of both root and reply posts.

## sub001 - Reply descendants of root posts

- Updated `ContentDeleteService.collectReplyIds()` to recurse through reply descendants instead of stopping at the first reply level.
- Extended `server/src/test/kotlin/straightway/mosa/content/ContentServiceTest.kt` to stub the descendant query for the new recursion path and keep the regression explicit.

## Verification

- `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.removes replies when deleting root post'` - passed.
- `./gradlew :server:test --tests 'straightway.mosa.content.complaint.PostComplaintIntegrationTest.identity deletion fails and leaves content intact when complaint cleanup fails'` - passed.
- `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$DeleteAll.removes all entries across repeated queries'` - passed.
- `./gradlew check` - passed.
- `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.keeps content when reply deletion fails after remove'` - passed on rerun while verifying TEST-4.
- `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$Delete.removes descendant replies when deleting root post' --tests 'straightway.mosa.content.ContentServiceTest$Delete.stops when reply graph cycles back to a visited post' --tests 'straightway.mosa.content.ContentServiceTest$Delete.preserves original delete failure when rollback restore also fails'` - passed.
- `./gradlew check` - passed after the content-delete fixes; a parallel run failed earlier because another Gradle job reused the same build directory and left `server/build/test-results/test/binary/in-progress-results-*.bin` missing.
- `./gradlew :server:test --tests 'straightway.mosa.content.complaint.PostComplaintIntegrationTest.identity deletion fails and leaves content intact when complaint cleanup fails'` - passed.
- `./gradlew :server:test --tests 'straightway.mosa.content.ContentServiceTest$DeleteAll.removes all entries across repeated queries' --tests 'straightway.mosa.content.ContentServiceTest$DeleteAll.removes descendants of user replies when deleting all content for user' --tests 'straightway.mosa.content.ContentServiceTest$Delete.preserves original delete failure when rollback restore also fails'` - passed on serial rerun; the concurrent execution failed because two Gradle test tasks contended for the same build output.
