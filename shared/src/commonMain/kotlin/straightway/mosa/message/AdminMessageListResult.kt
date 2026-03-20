package straightway.mosa.message

import kotlinx.serialization.Serializable

@Serializable
data class AdminMessageListResult(
    val entries: List<AdminMessage>,
    val page: Int = 1,
    val hasMore: Boolean = false,
)
