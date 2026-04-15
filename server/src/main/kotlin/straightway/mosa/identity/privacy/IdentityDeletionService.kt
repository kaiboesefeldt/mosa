package straightway.mosa.identity.privacy

import kotlinx.coroutines.runBlocking
import straightway.mosa.content.ContentService
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.identity.blocklist.BlockedIdentityStore
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.structuredstate.StructuredStateDatabase

class IdentityDeletionService(
    private val contentService: ContentService,
    private val database: StructuredStateDatabase,
    private val postComplaintStoreFactory: PostComplaintStoreFactory,
    private val adminMessageStoreFactory: AdminMessageStoreFactory,
    private val blockedIdentityStore: BlockedIdentityStore,
) : IdentityDeleter {
    override fun deleteIdentity(gid: String): DeleteIdentityResult =
        database.inTransaction { context ->
            val postComplaintStore = postComplaintStoreFactory.create(context)
            val adminMessageStore = adminMessageStoreFactory.create(context)
            blockedIdentityStore.delete(gid)
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
