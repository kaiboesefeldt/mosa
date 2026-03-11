package straightway.mosa.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ContentKind {
    @SerialName("post")
    POST,

    @SerialName("reply")
    REPLY,
}
