package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class PublishRequest(
    val text: String,
)
