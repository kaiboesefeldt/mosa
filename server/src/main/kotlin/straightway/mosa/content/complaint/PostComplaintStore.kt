package straightway.mosa.content.complaint

interface PostComplaintStore {
    suspend fun appendOpen(record: PostComplaintRecord)

    suspend fun loadLatestForPost(postId: String): PostComplaintRecord?

    suspend fun loadById(id: String): PostComplaintRecord?

    suspend fun countOpen(): Int

    suspend fun loadOpenPage(
        limit: Int,
        offset: Int,
    ): List<PostComplaintRecord>

    suspend fun close(
        complaintId: String,
        state: PostComplaintState,
        adminGid: String,
        decidedAt: Long,
        feedback: String,
        adminSignature: String,
        adminSignatureVersion: Int,
    ): Boolean
}
