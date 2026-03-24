package straightway.mosa.client.content.complaint

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.complaint.PostComplaintAvailabilityResult

class PostComplaintService(
    private val podSessionProvider: PodSessionProvider,
    private val postComplaintSigner: PostComplaintSigner,
) {
    suspend fun status(postId: String): PostComplaintAvailabilityResult? {
        val trimmedPostId = postId.trim()
        if (trimmedPostId.isEmpty()) {
            return null
        }
        return podSessionProvider.execute("post complaint status") { session ->
            session.podAccess.postComplaintStatus(trimmedPostId, session.jwt)
        }
    }

    suspend fun submit(
        postId: String,
        reason: String,
    ): Boolean {
        val trimmedPostId = postId.trim()
        val trimmedReason = reason.trim()
        if (trimmedPostId.isEmpty() || trimmedReason.isEmpty()) {
            return false
        }
        return podSessionProvider.execute("post complaint") { session ->
            session.podAccess.createPostComplaint(
                postComplaintSigner.signComplaint(
                    identity = session.identity,
                    postId = trimmedPostId,
                    reason = trimmedReason,
                ),
                session.jwt,
            )
            true
        } ?: false
    }
}
