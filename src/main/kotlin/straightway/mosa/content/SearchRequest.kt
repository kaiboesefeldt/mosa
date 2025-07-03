package straightway.mosa.content

data class SearchRequest(
    val embeddingVersion: String,
    val embeddingVector: List<Float>,
    val minSimilarity: Float,
    val maxResults: Int,
)
