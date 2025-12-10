package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class QueryRequest(
    val topic: String? = null,
    val fromTime: Long? = null,
    val toTime: Long? = null,
    val threadRootId: String? = null,
    val scoreMustBeAbove: Float? = null,
    val scoreMustBeBelow: Float? = null,
)
