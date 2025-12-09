package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class FetchRequest(
    val itemIds: List<String>,
)
