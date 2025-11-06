package straightway.mosa.vectorstore

import org.apache.lucene.document.Document
import org.apache.lucene.document.DocumentStoredFieldVisitor
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.TotalHits
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.QueryResult
import straightway.mosa.VectorStore.QueryResults
import java.time.Clock
import java.util.UUID

class LuceneLocalVectorStore private constructor(
    private val indexAccess: LuceneIndexAccess,
    private val paginationRegistry: QueryOnRegistry,
    private val queryPlanner: LuceneQueryPlanner,
    private val clock: Clock,
    private val vectorSize: Int,
) : VectorStore {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        indexAccess.close()
    }

    override suspend fun writeAccess(transactionBlock: suspend VectorStore.Transaction.() -> Unit) {
        checkClosed()
        indexAccess.write {
            indexAccess.transaction(clock, vectorSize).transactionBlock()
        }
    }

    override suspend fun query(query: VectorStore.Query): QueryResults {
        checkClosed()

        return runCatching {
            indexAccess.read {
                val searcher = IndexSearcher(this)
                val luceneQuery = queryPlanner.create(query)
                val topDocs =
                    if (query.vector == null) {
                        searcher.search(luceneQuery, pageSize, SORT_BY_TIME_ADDED)
                    } else {
                        searcher.search(luceneQuery, pageSize)
                    }
                val resultsPage =
                    topDocs
                        .scoreDocs
                        .map { it.toSearchResult(this) }
                val searchOnId =
                    if (resultsPage.isNotFullPage() || topDocs.areResultsExhausted()) {
                        null
                    } else {
                        paginationRegistry.register(
                            topDocs,
                            luceneQuery,
                            if (query.vector == null) SORT_BY_TIME_ADDED else null,
                        )
                    }

                QueryResults(resultsPage, searchOnId)
            }
        }.onFailure { if (it !is IndexNotFoundException) throw it }
            .getOrDefault(QueryResults(resultsPage = emptyList(), queryOnId = null))
    }

    override suspend fun queryOn(queryOnId: UUID?): QueryResults {
        checkClosed()

        if (queryOnId == null) {
            return QueryResults(emptyList(), null)
        }

        val queryOnRecord =
            paginationRegistry.get(queryOnId)
                ?: return QueryResults(emptyList(), null)

        return indexAccess.read {
            val searcher = IndexSearcher(this)
            val topDocs =
                with(queryOnRecord) {
                    if (sort == null) {
                        searcher.searchAfter(after, query, n)
                    } else {
                        searcher.searchAfter(after, query, n, sort)
                    }
                }

            paginationRegistry.clear(queryOnId)

            val resultsPage =
                topDocs
                    .scoreDocs
                    .map { it.toSearchResult(this) }

            val nextSearchOnId =
                if (resultsPage.isNotFullPage() || topDocs.areResultsExhausted(resultsSoFar = queryOnRecord.n)) {
                    null
                } else {
                    paginationRegistry.register(
                        topDocs,
                        queryOnRecord.query,
                        queryOnRecord.sort,
                    )
                }

            QueryResults(resultsPage = resultsPage, queryOnId = nextSearchOnId)
        }
    }

    private val pageSize = paginationRegistry.pageSize

    private fun List<QueryResult>.isNotFullPage(): Boolean = size < pageSize

    private fun TopDocs.areResultsExhausted(resultsSoFar: Int = 0): Boolean =
        totalHits.value == (resultsSoFar + pageSize.toLong()) && totalHits.relation == TotalHits.Relation.EQUAL_TO

    private fun ScoreDoc.toSearchResult(reader: DirectoryReader): QueryResult {
        val doc = getDocument(reader)
        val fields = doc.getFieldMap()
        return QueryResult(
            id = doc[ID_FIELD_NAME],
            text = doc[TEXT_FIELD_NAME],
            similarity = this.score,
            fields = fields,
        )
    }

    private fun ScoreDoc.getDocument(reader: DirectoryReader): Document {
        val visitor = DocumentStoredFieldVisitor()
        reader.document(doc, visitor)
        return visitor.document
    }

    private fun Document.getFieldMap(): Map<String, String> =
        fields
            .filter { it.name() !in SPECIAL_FIELDS }
            .associate { it.name() to it.storedValue().stringValue }

    private fun checkClosed() {
        if (closed) throw RuntimeException("The vector store has already been closed")
    }

    companion object {
        fun create(
            indexAccess: LuceneIndexAccess,
            paginationRegistry: QueryOnRegistry,
            queryPlanner: LuceneQueryPlanner,
            clock: Clock,
            vectorSize: Int,
        ): VectorStore =
            LuceneLocalVectorStore(
                indexAccess,
                paginationRegistry,
                queryPlanner,
                clock,
                vectorSize,
            )
    }
}
