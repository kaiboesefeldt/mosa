package straightway.mosa.client.content.complaint

import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.complaint.OpenPostComplaintCountResult
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState

class AdminPostComplaintService(
    private val podSessionProvider: PodSessionProvider,
    private val postComplaintSigner: PostComplaintSigner,
    private val timeService: TimeService,
) {
    private var cachedCount: CachedOpenPostComplaintCount? = null

    suspend fun countOpen(forceRefresh: Boolean = false): OpenPostComplaintCountResult? {
        val cached = cachedCount
        if (!forceRefresh && cached != null && timeService.now < cached.expiresAt) {
            return cached.result
        }
        val result =
            podSessionProvider.execute("open post complaint count") { session ->
                session.podAccess.openPostComplaintCount(session.jwt)
            }
        cachedCount =
            result?.let {
                CachedOpenPostComplaintCount(
                    result = it,
                    expiresAt = timeService.now + countCacheLifetimeSeconds,
                )
            }
        return result
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
            cachedCount = null
            true
        } ?: false
    }

    private data class CachedOpenPostComplaintCount(
        val result: OpenPostComplaintCountResult,
        val expiresAt: Long,
    )

    private companion object {
        const val countCacheLifetimeSeconds = 10L
    }
}
