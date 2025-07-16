package straightway.mosa

fun interface RerankingService {
    data class ScoredDocument(
        val score: Float,
        val document: String,
    )

    suspend fun rank(
        query: String,
        documents: List<String>,
    ): List<ScoredDocument>
}
