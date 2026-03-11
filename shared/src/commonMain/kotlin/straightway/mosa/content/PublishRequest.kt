package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class PublishRequest(
    val text: String,
    val signature: String = "",
    val signatureVersion: Int = ContentSignatureVersion.V1,
)
