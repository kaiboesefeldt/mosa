package straightway.mosa.content

import kotlinx.coroutines.runBlocking
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.message.AdminMessageStoreFactory

class IdentityDeletionService(
    private val contentService: ContentService,
    private val database: StructuredStateDatabase,
    private val postComplaintStoreFactory: PostComplaintStoreFactory,
    private val adminMessageStoreFactory: AdminMessageStoreFactory,
) {
    fun deleteIdentity(gid: String): DeleteIdentityResult =
        database.inTransaction { context ->
            val postComplaintStore = postComplaintStoreFactory.create(context)
            val adminMessageStore = adminMessageStoreFactory.create(context)
            val deletedComplaintCount = postComplaintStore.deleteForIdentity(gid)
            val deletedAdminMessageCount = adminMessageStore.deleteForIdentity(gid)
            val deletedPostIds = runBlocking { contentService.deleteAllForUser(gid) }
            DeleteIdentityResult(
                deletedPostIds = deletedPostIds,
                deletedComplaintCount = deletedComplaintCount,
                deletedAdminMessageCount = deletedAdminMessageCount,
            )
        }
}
