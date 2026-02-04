package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class DeletePostsResult(
    val ids: List<String>,
)
