package straightway.mosa

import java.util.UUID

interface VectorStore {
    data class QueryResults(
        val resultsPage: List<QueryResult>,
        val queryOnId: UUID?,
    )

    data class QueryResult(
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

    suspend fun query(vector: FloatArray): QueryResults

    suspend fun queryOn(queryOnId: UUID?): QueryResults
}
