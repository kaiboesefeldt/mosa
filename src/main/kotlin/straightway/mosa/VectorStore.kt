package straightway.mosa

import java.util.UUID

interface VectorStore {
    data class SearchResults(
        val resultsPage: List<SearchResult>,
        val queryOnId: UUID?,
    )

    data class SearchResult(
        val id: String,
        val text: String,
        val similarity: Float,
        val fields: Map<String, String>,
    )

    interface Transaction {
        suspend fun add(
            id: String,
            text: String,
            fields: Map<String, String>,
            vector: FloatArray,
        )

        suspend fun remove(id: String)
    }

    suspend fun writeAccess(transactionBlock: suspend Transaction.() -> Unit)

    suspend fun query(
        vector: FloatArray,
        minSimilarity: Float,
    ): SearchResults

    suspend fun searchOn(searchOnId: UUID?): SearchResults
}
