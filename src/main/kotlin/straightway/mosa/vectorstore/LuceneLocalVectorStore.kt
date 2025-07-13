package straightway.mosa.vectorstore

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.DocumentStoredFieldVisitor
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.DirectoryReader.openIfChanged
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.search.TotalHits
import org.apache.lucene.store.Directory
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.QueryResult
import straightway.mosa.VectorStore.QueryResults
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap

class LuceneLocalVectorStore(
    directoryFactory: () -> Directory,
    private val maxResults: Int,
    private val pageSize: Int,
    private val maxNotFinishedQueries: Int,
    private val vectorSize: Int,
) : VectorStore,
    AutoCloseable {
    private data class SearchOnRecord(
        val after: ScoreDoc,
        val query: Query,
        val n: Int,
        val minSimilarity: Float,
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
    private val searchOnRecords: ConcurrentMap<UUID, SearchOnRecord> =
        ConcurrentHashMap<UUID, SearchOnRecord>()
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

    override suspend fun query(
        vector: FloatArray,
        minSimilarity: Float,
    ): QueryResults {
        checkClosed()

        return runCatching {
            access.read {
                val searcher = IndexSearcher(this)
                val knnQuery =
                    KnnFloatVectorQuery(
                        VECTOR_FIELD_NAME,
                        vector,
                        maxResults,
                    )
                val topDocs = searcher.search(knnQuery, pageSize)
                val resultsPage =
                    topDocs
                        .scoreDocs
                        .filter { minSimilarity <= it.score }
                        .map { it.toSearchResult(this) }
                val searchOnId =
                    if (resultsPage.isNotFullPage() || topDocs.areResultsExhausted()) {
                        null
                    } else {
                        createSearchOnId(topDocs, knnQuery, minSimilarity)
                    }

                QueryResults(resultsPage, searchOnId)
            }
        }.onFailure { if (it !is IndexNotFoundException) throw it }
            .getOrDefault(QueryResults(resultsPage = emptyList(), queryOnId = null))
    }

    override suspend fun queryOn(queryOnId: UUID?): QueryResults {
        checkClosed()

        if (queryOnId === null) {
            return QueryResults(emptyList(), null)
        }

        val searchOnRecord =
            searchOnRecords[queryOnId]
                ?: return QueryResults(emptyList(), null)

        return access.read {
            val searcher = IndexSearcher(this)
            val topDocs =
                with(searchOnRecord) {
                    searcher.searchAfter(after, query, n)
                }

            clearSearchOnFor(queryOnId)

            val resultsPage =
                topDocs
                    .scoreDocs
                    .filter { searchOnRecord.minSimilarity <= it.score }
                    .map { it.toSearchResult(this) }

            val nextSearchOnId =
                if (resultsPage.isNotFullPage() || topDocs.areResultsExhausted(resultsSoFar = searchOnRecord.n)) {
                    null
                } else {
                    createSearchOnId(
                        topDocs,
                        searchOnRecord.query,
                        searchOnRecord.minSimilarity,
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
        minSimilarity: Float,
    ): UUID =
        UUID.randomUUID().also {
            registerForSearchOn(it, query, topDocs.scoreDocs.last(), minSimilarity)
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
        minSimilarity: Float,
    ) {
        removeSurplusSearchOnRecords()
        searchOnHistory.add(searchOnId)
        searchOnRecords.put(
            searchOnId,
            SearchOnRecord(
                after = after,
                query = query,
                n = pageSize,
                minSimilarity = minSimilarity,
            ),
        )

        assert(searchOnRecords.size == searchOnHistory.size)
    }

    private fun removeSurplusSearchOnRecords() {
        while (maxNotFinishedQueries <= searchOnRecords.size) {
            val entryToRemove = searchOnHistory.remove()
            searchOnRecords.remove(entryToRemove)
        }
    }

    private fun clearSearchOnFor(searchOnId: UUID) {
        searchOnRecords.remove(searchOnId)
        searchOnHistory.remove(searchOnId)
    }

    private fun checkClosed() {
        if (closed) throw RuntimeException("The vector store has already been closed")
    }

    private companion object {
        private const val ID_FIELD_NAME = "id"
        private const val TEXT_FIELD_NAME = "text"
        private const val VECTOR_FIELD_NAME = "vector"
        private val SPECIAL_FIELDS = setOf(ID_FIELD_NAME, TEXT_FIELD_NAME, VECTOR_FIELD_NAME)
    }
}
