package straightway.mosa.content

import java.time.Instant

data class QueryRequest(
    val topic: String? = null,
    val minTime: Instant? = null,
    val threadRootId: String? = null,
)

