package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val entries: List<Entry>,
    val queryOnId: String,
) {
    @Serializable
    data class Entry(
        val id: String,
        val similarity: Float,
    )
}
