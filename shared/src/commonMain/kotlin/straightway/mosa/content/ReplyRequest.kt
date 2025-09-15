package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class ReplyRequest(
    val content: PublishRequest,
    val rootId: String,
)
