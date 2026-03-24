package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
data class DecidePostComplaintRequest(
    val complaintId: String,
    val postId: String,
    val targetStatus: PostComplaintState,
    val feedback: String,
    val signature: String = "",
    val signatureVersion: Int = PostComplaintSignatureVersion.V1,
)
