package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
data class OpenPostComplaint(
    val complaintId: String,
    val postId: String,
    val postText: String,
    val postAuthorGid: String,
    val postCreatedAt: Long,
    val complainantGid: String,
    val reason: String,
    val createdAt: Long,
)
