package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
data class CreatePostComplaintRequest(
    val postId: String,
    val reason: String,
    val signature: String = "",
    val signatureVersion: Int = PostComplaintSignatureVersion.V1,
)
