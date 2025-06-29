package straightway.mosa.vectorstore

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.DocumentStoredFieldVisitor
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.DirectoryReader.openIfChanged
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.store.Directory
import straightway.mosa.VectorStore
import straightway.mosa.VectorStore.SearchResult
import straightway.mosa.VectorStore.SearchResults
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LuceneLocalVectorStore(
    directoryFactory: () -> Directory,
    private val maxResults: Int,
    private val pageSize: Int,
    private val maxNotFinishedSearches: Int,
    private val vectorSize: Int,
) : VectorStore,
    AutoCloseable {
    private data class SearchOnRecord(
        val after: ScoreDoc,
        val query: Query,
        val n: Int,
    )

    private inner class ReaderWriter : AutoCloseable {
        private var reader = DirectoryReader.open(directory)
        private val readerLock = ReentrantReadWriteLock()
        private val writer = IndexWriter(directory, config)

        fun <T> read(block: DirectoryReader.() -> T): T =
            readerLock.read {
                reader.block()
            }

        fun <T> write(block: IndexWriter.() -> T): T =
            readerLock.write {
                writer.block().also {
                    writer.commit()
                    openIfChanged(reader)?.let {
                        reader.close()
                        reader = it
                    }
                }
            }

        override fun close() {
            reader.close()
        }
    }

    private inner class Transaction(
        private val writer: IndexWriter,
    ) : VectorStore.Transaction {
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

            remove(id)
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
    private val access: ReaderWriter = ReaderWriter()
    private val config = IndexWriterConfig(analyzer)
    private val searchOnRecords: ConcurrentMap<UUID, SearchOnRecord> =
        ConcurrentHashMap<UUID, SearchOnRecord>()
    private val searchOnHistory = ConcurrentLinkedQueue<UUID>()
    private var closed = false

    init {
        if (maxNotFinishedSearches < 1) {
            throw RuntimeException(
                "maxNotFinishedSearches must be >= 1 (actual: $maxNotFinishedSearches)",
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

    override suspend fun writeAccess(transactionBlock: VectorStore.Transaction.() -> Unit) {
        checkClosed()
        access.write {
            Transaction(this).transactionBlock()
        }
    }

    override suspend fun search(
        vector: FloatArray,
        minSimilarity: Float,
    ): SearchResults {
        checkClosed()

        return access.read {
            val searcher = IndexSearcher(this)
            val knnQuery =
                KnnFloatVectorQuery(
                    VECTOR_FIELD_NAME,
                    vector,
                    maxResults,
                )
            val topDocs = searcher.search(knnQuery, pageSize)
            val searchOnId = UUID.randomUUID()

            if (topDocs.scoreDocs.any()) {
                registerForSearchOn(searchOnId, knnQuery, topDocs.scoreDocs.last())
            }

            SearchResults(
                resultsPage = topDocs.scoreDocs.map { it.toSearchResult(this) },
                searchOnId = searchOnId,
            )
        }
    }

    override suspend fun searchOn(searchOnId: UUID): SearchResults {
        checkClosed()

        val searchOnRecord =
            searchOnRecords[searchOnId]
                ?: return SearchResults(emptyList(), searchOnId)

        return access.read {
            val searcher = IndexSearcher(this)
            val topDocs =
                with(searchOnRecord) {
                    searcher.searchAfter(after, query, n)
                }

            if (topDocs.scoreDocs.isEmpty()) {
                clearSearchOnFor(searchOnId)
            }

            SearchResults(
                resultsPage = topDocs.scoreDocs.map { it.toSearchResult(this) },
                searchOnId = searchOnId,
            )
        }
    }

    private fun ScoreDoc.toSearchResult(reader: DirectoryReader): SearchResult {
        val doc = getDocument(reader)
        val fields = doc.getFieldMap()
        return SearchResult(
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
        knnQuery: KnnFloatVectorQuery,
        after: ScoreDoc,
    ) {
        removeSurplusSearchOnRecords()
        searchOnHistory.add(searchOnId)
        searchOnRecords.put(
            searchOnId,
            SearchOnRecord(
                after = after,
                query = knnQuery,
                n = pageSize,
            ),
        )

        assert(searchOnRecords.size == searchOnHistory.size)
    }

    private fun removeSurplusSearchOnRecords() {
        while (maxNotFinishedSearches <= searchOnRecords.size) {
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
