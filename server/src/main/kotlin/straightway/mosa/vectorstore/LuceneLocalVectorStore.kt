package straightway.mosa.vectorstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.lucene.document.Document
import org.apache.lucene.document.DocumentStoredFieldVisitor
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
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
                val searchResult =
                    if (query.vector == null) {
                        searcher
                            .search(luceneQuery, pageSize, SORT_BY_TIME_ADDED)
                            .unfilteredSearchResult()
                    } else {
                        with(searcher) {
                            afterKnnFilter(search(luceneQuery, pageSize), query, luceneQuery, 0)
                        }
                    }

                val resultsPage = searchResult.filtered.scoreDocs.map { it.toSearchResult(this) }
                val searchOnId =
                    if (searchResult.unfilteredDocs == 0) {
                        null
                    } else {
                        paginationRegistry
                            .register(
                                query,
                                searchResult.filtered,
                                luceneQuery,
                                if (query.vector == null) SORT_BY_TIME_ADDED else null,
                                hitsSoFar = searchResult.unfilteredDocs,
                            ).takeIf { searchResult.isFurtherPaginationPossible }
                    }

                QueryResults(resultsPage, searchOnId)
            }
        }.onFailure {
            if (it !is IndexNotFoundException) throw it
        }.getOrDefault(QueryResults(resultsPage = emptyList(), queryOnId = null))
    }

    override suspend fun queryOn(queryOnId: UUID?): QueryResults {
        checkClosed()

        if (queryOnId == null) {
            return QueryResults(emptyList(), null)
        }

        val queryOnRecord =
            paginationRegistry.get(queryOnId)
                ?: return QueryResults(emptyList(), null)

        return runCatching {
            indexAccess.read {
                val searcher = IndexSearcher(this)
                val docs =
                    with(queryOnRecord) {
                        if (sort != null) {
                            searcher.searchAfter(after, query, n, sort).unfilteredSearchResult()
                        } else {
                            with(searcher) {
                                afterKnnFilter(
                                    searcher.searchAfter(after, query, n),
                                    queryOnRecord.vectorStoreQuery,
                                    queryOnRecord.query,
                                    queryOnRecord.hitsSoFar,
                                )
                            }
                        }
                    }

                paginationRegistry.clear(queryOnId)

                val resultsPage = docs.filtered.scoreDocs.map { it.toSearchResult(this) }

                val nextSearchOnId =
                    if (docs.unfilteredDocs == 0) {
                        null
                    } else {
                        paginationRegistry
                            .register(
                                queryOnRecord.vectorStoreQuery,
                                docs.filtered,
                                queryOnRecord.query,
                                queryOnRecord.sort,
                                hitsSoFar = queryOnRecord.hitsSoFar + docs.unfilteredDocs,
                            ).takeIf { docs.isFurtherPaginationPossible }
                    }

                QueryResults(resultsPage = resultsPage, queryOnId = nextSearchOnId)
            }
        }.onFailure {
            if (it !is IndexNotFoundException) throw it
        }.getOrDefault(QueryResults(resultsPage = emptyList(), queryOnId = null))
    }

    override fun dumpAll(): Flow<VectorStore.PostDump> {
        checkClosed()

        return flow {
            var after: ScoreDoc? = null
            while (true) {
                val page =
                    runCatching {
                        indexAccess.read {
                            val searcher = IndexSearcher(this)
                            val topDocs =
                                searcher.searchAfter(
                                    after,
                                    MatchAllDocsQuery(),
                                    pageSize,
                                    SORT_BY_TIME_ADDED,
                                )
                            after = topDocs.scoreDocs.lastOrNull()
                            topDocs.scoreDocs.map { it.toPostDump(this) }
                        }
                    }.onFailure {
                        if (it !is IndexNotFoundException) throw it
                    }.getOrDefault(emptyList())

                if (page.isEmpty()) break
                page.forEach { emit(it) }
                if (page.size < pageSize) break
            }
        }
    }

    private val pageSize = paginationRegistry.pageSize

    private fun TopDocs.isFullPage(): Boolean = pageSize <= scoreDocs.size

    private data class FilteredSearchResult(
        val filtered: TopDocs,
        val unfilteredDocs: Int,
        val isFurtherPaginationPossible: Boolean,
    ) {
        companion object {
            val EMPTY =
                FilteredSearchResult(
                    TopDocs(TotalHits(0, TotalHits.Relation.EQUAL_TO), emptyArray()),
                    unfilteredDocs = 0,
                    isFurtherPaginationPossible = false,
                )
        }
    }

    private fun TopDocs.unfilteredSearchResult() =
        FilteredSearchResult(
            this,
            unfilteredDocs = scoreDocs.size,
            isFurtherPaginationPossible = isFullPage() && !areResultsExhausted(0),
        )

    private tailrec fun IndexSearcher.afterKnnFilter(
        docs: TopDocs,
        query: VectorStore.Query,
        luceneQuery: Query,
        resultsSoFar: Int,
    ): FilteredSearchResult {
        val firstDocMatch by lazy { query.matchesScoreRangeFor(docs.scoreDocs.first()) }
        val lastDocMatch by lazy { query.matchesScoreRangeFor(docs.scoreDocs.last()) }

        return when {
            docs.scoreDocs.isEmpty() -> FilteredSearchResult.EMPTY
            firstDocMatch == ScoreRangeMatch.ScoreTooLow -> FilteredSearchResult.EMPTY
            else -> {
                val matches =
                    docs.scoreDocs
                        .filter { query.matchesScoreRangeFor(it) == ScoreRangeMatch.ScoreMatches }
                        .toTypedArray()
                if (matches.any()) {
                    FilteredSearchResult(
                        TopDocs(
                            TotalHits(
                                matches.size.toLong(),
                                TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO,
                            ),
                            matches,
                        ),
                        isFurtherPaginationPossible =
                            (lastDocMatch == ScoreRangeMatch.ScoreMatches) && !docs.areResultsExhausted(resultsSoFar),
                        unfilteredDocs = docs.scoreDocs.size,
                    )
                } else {
                    afterKnnFilter(
                        searchAfter(docs.scoreDocs.last(), luceneQuery, pageSize),
                        query,
                        luceneQuery,
                        resultsSoFar + docs.scoreDocs.size,
                    )
                }
            }
        }
    }

    private enum class ScoreRangeMatch { ScoreTooHigh, ScoreMatches, ScoreTooLow }

    private fun VectorStore.Query.matchesScoreRangeFor(doc: ScoreDoc): ScoreRangeMatch {
        scoreMustBeAbove?.let { if (doc.score <= it) return ScoreRangeMatch.ScoreTooLow }
        scoreMustBeBelow?.let { if (it <= doc.score) return ScoreRangeMatch.ScoreTooHigh }
        return ScoreRangeMatch.ScoreMatches
    }

    private fun TopDocs.areResultsExhausted(resultsSoFar: Int = 0): Boolean =
        totalHits.value == (resultsSoFar + scoreDocs.size.toLong()) && totalHits.relation == TotalHits.Relation.EQUAL_TO

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

    private fun ScoreDoc.toPostDump(reader: DirectoryReader): VectorStore.PostDump {
        val doc = getDocument(reader)
        val fields = doc.getFieldMap()
        return VectorStore.PostDump(
            id = doc[ID_FIELD_NAME],
            text = doc[TEXT_FIELD_NAME],
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
