package straightway.mosa.content.complaint

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PostComplaintSignaturePayload(
    @SerialName("v")
    val version: Int,
    val type: PostComplaintPayloadType,
    val postId: String,
    val reason: String,
) {
    fun toCanonicalBytes(): ByteArray = canonicalJson.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val canonicalJson = Json

        fun forComplaint(
            postId: String,
            reason: String,
        ) = PostComplaintSignaturePayload(
            version = PostComplaintSignatureVersion.V1,
            type = PostComplaintPayloadType.POST_COMPLAINT,
            postId = postId,
            reason = reason,
        )
    }
}
