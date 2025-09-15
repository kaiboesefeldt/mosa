package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class QueryRequest(
    val topic: String? = null,
    // epoch seconds; null means unset
    val minTime: Long? = null,
    val threadRootId: String? = null,
)
