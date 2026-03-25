package straightway.mosa.content.complaint

interface PostComplaintStore {
    suspend fun appendOpen(record: PostComplaintRecord)

    suspend fun loadLatestForPost(postId: String): PostComplaintRecord?

    suspend fun loadById(id: String): PostComplaintRecord?

    suspend fun countOpen(): Int

    suspend fun loadPage(
        limit: Int,
        offset: Int,
        includeProcessed: Boolean,
    ): List<PostComplaintRecord>

    suspend fun decide(
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
