package straightway.mosa

interface VectorStore {
    data class SearchResult(
        val similarity: Float,
        val text: String
    )
    suspend fun add(id: String, vector: FloatArray)
    suspend fun search(vector: FloatArray, minSimilarity: Float): List<SearchResult>
}