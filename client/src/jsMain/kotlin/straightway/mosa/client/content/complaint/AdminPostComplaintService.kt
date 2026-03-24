package straightway.mosa.client.content.complaint

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.complaint.OpenPostComplaintCountResult
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState

class AdminPostComplaintService(
    private val podSessionProvider: PodSessionProvider,
    private val postComplaintSigner: PostComplaintSigner,
) {
    suspend fun countOpen(): OpenPostComplaintCountResult? =
        podSessionProvider.execute("open post complaint count") { session ->
            session.podAccess.openPostComplaintCount(session.jwt)
        }

    suspend fun listOpen(page: Int): OpenPostComplaintsResult? {
        if (page < 1) {
            return null
        }
        return podSessionProvider.execute("open post complaints") { session ->
            session.podAccess.openPostComplaints(session.jwt, page)
        }
    }

    suspend fun decide(
        complaintId: String,
        postId: String,
        targetStatus: PostComplaintState,
        feedback: String,
    ): Boolean {
        val trimmedComplaintId = complaintId.trim()
        val trimmedPostId = postId.trim()
        val trimmedFeedback = feedback.trim()
        if (trimmedComplaintId.isEmpty() || trimmedPostId.isEmpty() || trimmedFeedback.isEmpty()) {
            return false
        }
        if (targetStatus == PostComplaintState.OPEN) {
            return false
        }
        return podSessionProvider.execute("post complaint decision") { session ->
            session.podAccess.decidePostComplaint(
                postComplaintSigner.signDecision(
                    identity = session.identity,
                    complaintId = trimmedComplaintId,
                    postId = trimmedPostId,
                    targetStatus = targetStatus,
                    feedback = trimmedFeedback,
                ),
                session.jwt,
            )
            true
        } ?: false
    }
}
