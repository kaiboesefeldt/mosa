package straightway.mosa.content

import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.message.AdminMessageStore

class IdentityDeletionService(
    private val contentService: ContentService,
    private val postComplaintStore: PostComplaintStore,
    private val adminMessageStore: AdminMessageStore,
) {
    suspend fun deleteIdentity(gid: String): DeleteIdentityResult {
        val deletedPosts = contentService.loadAllForUser(gid)
        val deletedPostIds = contentService.deleteAllForUser(gid)
        return try {
            val deletedComplaintCount = postComplaintStore.deleteForIdentity(gid)
            val deletedAdminMessageCount = adminMessageStore.deleteForIdentity(gid)
            DeleteIdentityResult(
                deletedPostIds = deletedPostIds,
                deletedComplaintCount = deletedComplaintCount,
                deletedAdminMessageCount = deletedAdminMessageCount,
            )
        } catch (error: Throwable) {
            runCatching {
                contentService.restore(deletedPosts)
            }.getOrElse { restoreError ->
                error.addSuppressed(restoreError)
            }
            throw error
        }
    }
}
