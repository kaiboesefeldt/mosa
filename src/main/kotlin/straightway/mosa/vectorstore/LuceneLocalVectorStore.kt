package straightway.mosa.vectorstore

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.DocumentStoredFieldVisitor
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.DirectoryReader.openIfChanged
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.TotalHits
import org.apache.lucene.store.Directory
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.QueryResult
import straightway.mosa.VectorStore.QueryResults
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap

class LuceneLocalVectorStore(
    directoryFactory: () -> Directory,
    private val clock: Clock,
    private val maxResults: Int,
    private val pageSize: Int,
    private val maxNotFinishedQueries: Int,
    private val vectorSize: Int,
) : VectorStore,
    AutoCloseable {
    private data class QueryOnRecord(
        val after: ScoreDoc,
        val query: Query,
        val n: Int,
        val sort: Sort?,
    )

    private inner class ReaderWriter : AutoCloseable {
        private val writer = IndexWriter(directory, config)
        private var reader: DirectoryReader? = null
        private val readerLock = Mutex()

        suspend fun <T> read(block: DirectoryReader.() -> T): T =
            readerLock.withLock {
                makeSureReaderIsInitialized()
                reader!!.block()
            }

        suspend fun <T> write(block: suspend IndexWriter.() -> T): T =
            readerLock.withLock {
                writer.block().also {
                    writer.commit()
                    reader?.let { oldReader ->
                        openIfChanged(reader)?.let {
                            oldReader.close()
                            reader = it
                        }
                    }
                }
            }

        fun makeSureReaderIsInitialized(): DirectoryReader {
            val nullSafeReader = reader ?: DirectoryReader.open(directory)
            reader = nullSafeReader
            return nullSafeReader
        }

        fun transaction(): Transaction =
            Transaction(
                runCatching { makeSureReaderIsInitialized() }.getOrNull(),
                writer,
            )

        override fun close() {
            reader?.close()
        }
    }

    private inner class Transaction(
        private val reader: DirectoryReader?,
        private val writer: IndexWriter,
    ) : VectorStore.Transaction {
        private val addedItemIdsInThisTransaction = mutableSetOf<String>()

        override suspend fun add(
            id: String,
            text: String,
            fields: Map<String, String>,
            vector: FloatArray,
        ) {
            if (vector.size != vectorSize) {
                throw RuntimeException(
                    "Vector size mismatch: ${vector.size} vs. $vectorSize",
                )
            }
            if ((fields.keys intersect SPECIAL_FIELDS).any()) {
                throw RuntimeException(
                    "Usage of special field name(s): ${(fields.keys intersect SPECIAL_FIELDS)}",
                )
            }

            if (id in addedItemIdsInThisTransaction || reader?.existsItemWithID(id) == true) {
                throw BadRequestException("Item with ID $id already exists")
            }
            addedItemIdsInThisTransaction.add(id)

            val doc = Document()
            doc.add(StringField(ID_FIELD_NAME, id, Field.Store.YES))
            doc.add(TextField(TEXT_FIELD_NAME, text, Field.Store.YES))
            doc.add(LongPoint(TIME_ADDED_FIELD_NAME, clock.instant().epochSecond))
            doc.add(NumericDocValuesField(TIME_ADDED_FIELD_NAME, clock.instant().epochSecond))
            fields.forEach { (key, value) ->
                doc.add(StringField(key, value, Field.Store.YES))
            }
            doc.add(KnnFloatVectorField(VECTOR_FIELD_NAME, vector, VectorSimilarityFunction.COSINE))

            writer.addDocument(doc)
        }

        override suspend fun remove(id: String) {
            writer.deleteDocuments(Term(ID_FIELD_NAME, id))
        }
    }

    private val directory: Directory = directoryFactory()
    private val analyzer: StandardAnalyzer = StandardAnalyzer()
    private val config = IndexWriterConfig(analyzer)
    private val access: ReaderWriter = ReaderWriter()
    private val queryOnRecords: ConcurrentMap<UUID, QueryOnRecord> =
        ConcurrentHashMap<UUID, QueryOnRecord>()
    private val searchOnHistory = ConcurrentLinkedQueue<UUID>()
    private var closed = false

    init {
        if (maxNotFinishedQueries < 1) {
            throw RuntimeException(
                "maxNotFinishedSearches must be >= 1 (actual: $maxNotFinishedQueries)",
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        access.close()
        analyzer.close()
        directory.close()
    }

    override suspend fun writeAccess(transactionBlock: suspend VectorStore.Transaction.() -> Unit) {
        checkClosed()
        access.write {
            access.transaction().transactionBlock()
        }
    }

    override suspend fun query(query: VectorStore.Query): QueryResults {
        checkClosed()

        return runCatching {
            access.read {
                val searcher = IndexSearcher(this)
                val luceneQuery = createLuceneQuery(query)
                val topDocs =
                    if (query.vector === null) {
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
                        createSearchOnId(
                            topDocs,
                            luceneQuery,
                            if (query.vector === null) SORT_BY_TIME_ADDED else null,
                        )
                    }

                QueryResults(resultsPage, searchOnId)
            }
        }.onFailure { if (it !is IndexNotFoundException) throw it }
            .getOrDefault(QueryResults(resultsPage = emptyList(), queryOnId = null))
    }

    private fun createLuceneQuery(query: VectorStore.Query): Query =
        BooleanQuery
            .Builder()
            .addKnnQuery(query.vector)
            .addTermQuery(ID_FIELD_NAME, query.id)
            .addTimeRangeQuery(TIME_ADDED_FIELD_NAME, query.fromTime, query.toTime)
            .apply { query.fields.forEach { key, value -> addTermQuery(key, value) } }
            .build()

    fun BooleanQuery.Builder.addKnnQuery(vector: FloatArray?) =
        also {
            if (vector !== null) {
                add(
                    KnnFloatVectorQuery(VECTOR_FIELD_NAME, vector, maxResults),
                    BooleanClause.Occur.MUST,
                )
            }
        }

    fun BooleanQuery.Builder.addTermQuery(
        fieldName: String,
        value: String?,
    ) = also {
        if (value !== null) {
            add(
                TermQuery(Term(fieldName, value)),
                BooleanClause.Occur.FILTER,
            )
        }
    }

    fun BooleanQuery.Builder.addTimeRangeQuery(
        fieldName: String,
        from: Instant?,
        to: Instant?,
    ) = also {
        if (from !== null || to !== null) {
            add(
                LongPoint.newRangeQuery(
                    fieldName,
                    from?.epochSecond ?: Long.MIN_VALUE,
                    to?.epochSecond ?: Long.MAX_VALUE,
                ),
                BooleanClause.Occur.FILTER,
            )
        }
    }

    override suspend fun queryOn(queryOnId: UUID?): QueryResults {
        checkClosed()

        if (queryOnId === null) {
            return QueryResults(emptyList(), null)
        }

        val queryOnRecord =
            queryOnRecords[queryOnId]
                ?: return QueryResults(emptyList(), null)

        return access.read {
            val searcher = IndexSearcher(this)
            val topDocs =
                with(queryOnRecord) {
                    if (sort === null) {
                        searcher.searchAfter(after, query, n)
                    } else {
                        searcher.searchAfter(after, query, n, sort)
                    }
                }

            clearSearchOnFor(queryOnId)

            val resultsPage =
                topDocs
                    .scoreDocs
                    .map { it.toSearchResult(this) }

            val nextSearchOnId =
                if (resultsPage.isNotFullPage() || topDocs.areResultsExhausted(resultsSoFar = queryOnRecord.n)) {
                    null
                } else {
                    createSearchOnId(
                        topDocs,
                        queryOnRecord.query,
                        queryOnRecord.sort,
                    )
                }

            QueryResults(resultsPage = resultsPage, queryOnId = nextSearchOnId)
        }
    }

    private fun DirectoryReader.existsItemWithID(itemID: String): Boolean {
        val searcher = IndexSearcher(this)
        val searchResult = searcher.search(TermQuery(Term(ID_FIELD_NAME, itemID)), 1)
        return 0 < searchResult.scoreDocs.size
    }

    private fun List<QueryResult>.isNotFullPage(): Boolean = size < pageSize

    private fun TopDocs.areResultsExhausted(resultsSoFar: Int = 0): Boolean =
        totalHits.value == (resultsSoFar + pageSize.toLong()) && totalHits.relation == TotalHits.Relation.EQUAL_TO

    private fun createSearchOnId(
        topDocs: TopDocs,
        query: Query,
        sort: Sort?,
    ): UUID =
        UUID.randomUUID().also {
            registerForSearchOn(it, query, topDocs.scoreDocs.last(), sort)
        }

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

    private fun registerForSearchOn(
        searchOnId: UUID,
        query: Query,
        after: ScoreDoc,
        sort: Sort?,
    ) {
        removeSurplusSearchOnRecords()
        searchOnHistory.add(searchOnId)
        queryOnRecords.put(
            searchOnId,
            QueryOnRecord(
                after = after,
                query = query,
                n = pageSize,
                sort = sort,
            ),
        )

        assert(queryOnRecords.size == searchOnHistory.size)
    }

    private fun removeSurplusSearchOnRecords() {
        while (maxNotFinishedQueries <= queryOnRecords.size) {
            val entryToRemove = searchOnHistory.remove()
            queryOnRecords.remove(entryToRemove)
        }
    }

    private fun clearSearchOnFor(searchOnId: UUID) {
        queryOnRecords.remove(searchOnId)
        searchOnHistory.remove(searchOnId)
    }

    private fun checkClosed() {
        if (closed) throw RuntimeException("The vector store has already been closed")
    }

    private companion object {
        private const val ID_FIELD_NAME = "id"
        private const val TEXT_FIELD_NAME = "text"
        private const val VECTOR_FIELD_NAME = "vector"
        private const val TIME_ADDED_FIELD_NAME = "timeadded"
        private val SPECIAL_FIELDS =
            setOf(
                ID_FIELD_NAME,
                TEXT_FIELD_NAME,
                VECTOR_FIELD_NAME,
                TIME_ADDED_FIELD_NAME,
            )
        private val SORT_BY_TIME_ADDED = Sort(SortField(TIME_ADDED_FIELD_NAME, SortField.Type.LONG, true))
    }
}
