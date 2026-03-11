package straightway.mosa.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ContentSignaturePayload(
    @SerialName("v")
    val version: Int,
    val kind: ContentKind,
    val text: String,
    val rootId: String,
) {
    fun toCanonicalBytes(): ByteArray = canonicalJson.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val canonicalJson = Json

        fun forPost(text: String) =
            ContentSignaturePayload(
                version = ContentSignatureVersion.V1,
                kind = ContentKind.POST,
                text = text,
                rootId = "",
            )

        fun forReply(
            text: String,
            rootId: String,
        ) = ContentSignaturePayload(
            version = ContentSignatureVersion.V1,
            kind = ContentKind.REPLY,
            text = text,
            rootId = rootId,
        )
    }
}
