package straightway.mosa.content

data class QueryRequest(
    val embeddingVersion: String,
    val embeddingVector: List<Float>,
    val maxResults: Int,
)
