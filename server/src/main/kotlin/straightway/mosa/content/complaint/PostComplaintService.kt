package straightway.mosa.content.complaint

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.content.ContentService
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.message.AdminMessageService
import straightway.mosa.message.CreateAdminMessageRequest
import java.time.Clock

class PostComplaintService(
    private val store: PostComplaintStore,
    private val contentService: ContentService,
    private val adminMessageService: AdminMessageService,
    private val clock: Clock,
    private val isAdmin: suspend (String) -> Boolean,
    private val idFactory: () -> String,
    private val openPageSize: Int,
) {
    suspend fun status(postId: String): PostComplaintAvailabilityResult {
        val post = loadPost(postId)
        val latest = store.loadLatestForPost(post.id)
        return PostComplaintAvailabilityResult(
            postId = post.id,
            availability =
                when (latest?.state) {
                    PostComplaintState.OPEN -> PostComplaintAvailability.OPEN
                    PostComplaintState.REJECTED, PostComplaintState.DELETED -> PostComplaintAvailability.BLOCKED
                    PostComplaintState.CONFIRMED, null -> PostComplaintAvailability.AVAILABLE
                },
        )
    }

    suspend fun create(
        complainantGid: String,
        request: CreatePostComplaintRequest,
    ) {
        requirePostId(request.postId)
        requireReason(request.reason)
        val post = loadPost(request.postId)
        if (post.authorGID == complainantGid) {
            throw BadRequestException("own posts cannot be complained about")
        }
        when (store.loadLatestForPost(post.id)?.state) {
            PostComplaintState.OPEN -> {
                throw PostComplaintConflictException("an open complaint already exists for this post")
            }

            PostComplaintState.REJECTED, PostComplaintState.DELETED -> {
                throw PostComplaintConflictException("new complaints are blocked for this post")
            }

            PostComplaintState.CONFIRMED, null -> {
                Unit
            }
        }
        store.appendOpen(
            PostComplaintRecord(
                id = idFactory(),
                postId = post.id,
                complainantGid = complainantGid,
                postAuthorGid = post.authorGID,
                reason = request.reason.trim(),
                createdAt = clock.instant().epochSecond,
                complainantSignature = request.signature,
                complainantSignatureVersion = request.signatureVersion,
                state = PostComplaintState.OPEN,
            ),
        )
    }

    suspend fun countOpen(requesterGid: String): OpenPostComplaintCountResult {
        requireAdmin(requesterGid)
        return OpenPostComplaintCountResult(store.countOpen())
    }

    suspend fun listOpen(
        requesterGid: String,
        page: Int,
        includeProcessed: Boolean,
    ): OpenPostComplaintsResult {
        requireAdmin(requesterGid)
        requirePage(page)
        val offset = (page - 1) * openPageSize
        val records = store.loadPage(limit = openPageSize + 1, offset = offset, includeProcessed = includeProcessed)
        val visible = records.take(openPageSize)
        val posts = contentService.fetch(FetchRequest(visible.map(PostComplaintRecord::postId))).entries.associateBy(FetchResult.Entry::id)
        return OpenPostComplaintsResult(
            entries =
                visible.map { record ->
                    val post = posts[record.postId] ?: throw ContentService.ContentNotFoundException()
                    OpenPostComplaint(
                        complaintId = record.id,
                        postId = record.postId,
                        postText = post.content,
                        postAuthorGid = post.authorGID,
                        postCreatedAt = post.storedAt,
                        complainantGid = record.complainantGid,
                        reason = record.reason,
                        createdAt = record.createdAt,
                        state = record.state,
                    )
                },
            page = page,
            hasMore = records.size > openPageSize,
        )
    }

    suspend fun decide(
        requesterGid: String,
        request: DecidePostComplaintRequest,
    ) {
        requireAdmin(requesterGid)
        requireComplaintId(request.complaintId)
        requirePostId(request.postId)
        requireFeedback(request.feedback)
        requireDecisionState(request.targetStatus)
        val record = store.loadById(request.complaintId) ?: throw ContentService.ContentNotFoundException()
        if (record.postId != request.postId) {
            throw BadRequestException("postId does not match complaint")
        }
        if (record.state == PostComplaintState.DELETED) {
            throw PostComplaintConflictException("deleted complaints cannot be changed")
        }
        if (record.state != PostComplaintState.OPEN && !canChangeClosedComplaint(record.state, request.targetStatus)) {
            throw PostComplaintConflictException("complaint state cannot be changed to requested target")
        }
        if (record.state == PostComplaintState.OPEN && request.targetStatus == PostComplaintState.DELETED) {
            contentService.delete(requesterGid, request.postId)
        }
        val decidedAt = clock.instant().epochSecond
        val updated =
            store.decide(
                complaintId = request.complaintId,
                expectedState = record.state,
                state = request.targetStatus,
                adminGid = requesterGid,
                decidedAt = decidedAt,
                feedback = request.feedback.trim(),
                adminSignature = request.signature,
                adminSignatureVersion = request.signatureVersion,
            )
        if (!updated) {
            throw PostComplaintConflictException("complaint is already closed")
        }
        adminMessageService.send(
            senderGid = requesterGid,
            request =
                CreateAdminMessageRequest(
                    recipientGid = record.complainantGid,
                    subject = ADMIN_MESSAGE_SUBJECT,
                    body = request.feedback.trim(),
                ),
        )
    }

    private suspend fun loadPost(postId: String): FetchResult.Entry {
        requirePostId(postId)
        return contentService.fetch(FetchRequest(listOf(postId))).entries.singleOrNull()
            ?: throw ContentService.ContentNotFoundException()
    }

    private suspend fun requireAdmin(gid: String) {
        if (!isAdmin(gid)) {
            throw PostComplaintForbiddenException()
        }
    }

    private fun requirePostId(postId: String) {
        if (postId.isBlank()) {
            throw BadRequestException("postId must not be blank")
        }
    }

    private fun requireComplaintId(complaintId: String) {
        if (complaintId.isBlank()) {
            throw BadRequestException("complaintId must not be blank")
        }
    }

    private fun requireReason(reason: String) {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            throw BadRequestException("reason must not be blank")
        }
        if (REASON_MAX_LENGTH < trimmed.length) {
            throw BadRequestException("reason must be at most $REASON_MAX_LENGTH characters")
        }
    }

    private fun requireFeedback(feedback: String) {
        val trimmed = feedback.trim()
        if (trimmed.isEmpty()) {
            throw BadRequestException("feedback must not be blank")
        }
        if (FEEDBACK_MAX_LENGTH < trimmed.length) {
            throw BadRequestException("feedback must be at most $FEEDBACK_MAX_LENGTH characters")
        }
    }

    private fun requirePage(page: Int) {
        if (page < 1) {
            throw BadRequestException("page must be at least 1")
        }
    }

    private fun requireDecisionState(targetStatus: PostComplaintState) {
        if (targetStatus == PostComplaintState.OPEN) {
            throw BadRequestException("targetStatus must close the complaint")
        }
    }

    private fun canChangeClosedComplaint(
        currentState: PostComplaintState,
        targetState: PostComplaintState,
    ): Boolean =
        when (currentState) {
            PostComplaintState.REJECTED, PostComplaintState.CONFIRMED -> {
                targetState == PostComplaintState.REJECTED || targetState == PostComplaintState.CONFIRMED
            }

            PostComplaintState.OPEN, PostComplaintState.DELETED -> {
                false
            }
        }

    private companion object {
        const val REASON_MAX_LENGTH = 500
        const val FEEDBACK_MAX_LENGTH = 1000
        const val ADMIN_MESSAGE_SUBJECT = "Complaint update"
    }
}
