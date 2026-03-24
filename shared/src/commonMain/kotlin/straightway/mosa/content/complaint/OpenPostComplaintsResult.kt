package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
data class OpenPostComplaintsResult(
    val entries: List<OpenPostComplaint>,
    val page: Int = 1,
    val hasMore: Boolean = false,
)
