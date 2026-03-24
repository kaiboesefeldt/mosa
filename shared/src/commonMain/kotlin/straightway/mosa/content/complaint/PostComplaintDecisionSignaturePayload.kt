package straightway.mosa.content.complaint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PostComplaintDecisionSignaturePayload(
    @SerialName("v")
    val version: Int,
    val type: PostComplaintPayloadType,
    val postId: String,
    val targetStatus: PostComplaintState,
    val feedback: String,
) {
    fun toCanonicalBytes(): ByteArray = canonicalJson.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val canonicalJson = Json

        fun forDecision(
            postId: String,
            targetStatus: PostComplaintState,
            feedback: String,
        ) = PostComplaintDecisionSignaturePayload(
            version = PostComplaintSignatureVersion.V1,
            type = PostComplaintPayloadType.POST_COMPLAINT_DECISION,
            postId = postId,
            targetStatus = targetStatus,
            feedback = feedback,
        )
    }
}
