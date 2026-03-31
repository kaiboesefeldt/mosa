package straightway.mosa.content.complaint

interface PostComplaintStore {
    fun appendOpen(record: PostComplaintRecord)

    fun deleteForIdentity(gid: String): Int

    fun loadLatestForPost(postId: String): PostComplaintRecord?

    fun loadById(id: String): PostComplaintRecord?

    fun countOpen(): Int

    fun loadPage(
        limit: Int,
        offset: Int,
        includeProcessed: Boolean,
    ): List<PostComplaintRecord>

    fun decide(
        complaintId: String,
        expectedState: PostComplaintState,
        state: PostComplaintState,
        adminGid: String,
        decidedAt: Long,
        feedback: String,
        adminSignature: String,
        adminSignatureVersion: Int,
    ): Boolean
}
