package straightway.mosa.client.topic

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import straightway.mosa.content.QueryResult
import kotlin.coroutines.resume
import kotlin.js.asDynamic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBOpenDBRequest
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.indexedDB
import web.idb.readonly
import web.idb.readwrite

class IndexedDbPostCacheTest {
    private val asyncBridge = IndexedDbAsyncBridge()

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `snapshot returns empty view without persisted data`() =
        runCacheTest { cache, _ ->
            val snapshot = cache.snapshot("topic")
            assertTrue(snapshot.entries.isEmpty())
            assertNull(snapshot.lastFetchedAt)
        }

    @Test
    fun `append stores entries and snapshot loads them`() =
        runCacheTest { cache, _ ->
            val entries =
                listOf(
                    entry("first", similarity = 0.2f, storedAt = 20),
                    entry("second", similarity = 0.9f, storedAt = 30),
                )

            val appended = cache.append("topic", entries)
            assertEquals(listOf("second", "first"), appended.entries.map { it.post.id })
            assertEquals(listOf("first", "second"), storedEntryIds().sorted())
            val topicRecord =
                storedTopicRecord("topic")
                    ?: fail("Missing topic record. Existing topics: ${storedTopicKeys().joinToString()}")
            val snapshot = cache.snapshot("topic")

            assertEquals(listOf("second", "first"), snapshot.entries.map { it.post.id })
            assertEquals(30, snapshot.lastFetchedAt)
        }

    @Test
    fun `topic specific similarity overrides entry payload`() =
        runCacheTest { cache, _ ->
            cache.append("alpha", listOf(entry("shared", similarity = 0.2f, storedAt = 10)))
            cache.append("beta", listOf(entry("shared", similarity = 0.85f, storedAt = 15)))

            val snapshot = cache.snapshot("beta")

            assertEquals(0.85f, snapshot.entries.single().similarity)
        }

    @Test
    fun `snapshot removes missing entry references`() =
        runCacheTest { cache, _ ->
            cache.append("topic", listOf(entry("orphaned", similarity = 0.4f, storedAt = 10)))
            deleteEntry("orphaned")

            val snapshot = cache.snapshot("topic")

            assertTrue(snapshot.entries.isEmpty())
        }

    @Test
    fun `append updates similarity for existing ids`() =
        runCacheTest { cache, _ ->
            cache.append("topic", listOf(entry("post", similarity = 0.1f, storedAt = 10)))

            cache.append("topic", listOf(entry("post", similarity = 0.8f, storedAt = 12)))
            val snapshot = cache.snapshot("topic")

            assertEquals(0.8f, snapshot.entries.single().similarity)
            assertEquals(12, snapshot.lastFetchedAt)
        }

    @Test
    fun `evicts oldest entries when exceeding limit`() =
        runCacheTest(maxEntries = 5) { cache, _ ->
            val entries = (1..7).map { index ->
                entry("p$index", similarity = index.toFloat(), storedAt = index.toLong())
            }

            cache.append("topic", entries)
            val snapshot = cache.snapshot("topic")

            assertEquals(5, snapshot.entries.size)
            assertEquals(listOf("p7", "p6", "p5", "p4", "p3"), snapshot.entries.map { it.post.id })
        }

    @Test
    fun `clear removes cached topic data`() =
        runCacheTest { cache, _ ->
            cache.append("topic", listOf(entry("post", similarity = 0.5f, storedAt = 10)))

            cache.clear("topic")
            val snapshot = cache.snapshot("topic")

            assertTrue(snapshot.entries.isEmpty())
            assertNull(snapshot.lastFetchedAt)
        }

    private fun entry(id: String, similarity: Float, storedAt: Long): QueryResult.Entry =
        QueryResult.Entry(
            id = id,
            content = "content-$id",
            similarity = similarity,
            authorGID = "author",
            storedAt = storedAt,
        )

    private suspend fun deleteEntry(id: String) {
        val factory = indexedDb()
        val request = factory.open(postCacheDatabaseName)
        val database = asyncBridge.awaitRequest<IDBDatabase>(request)
        try {
            val transaction = database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(postCacheEntriesStoreName)
            asyncBridge.awaitRequest<Any?>(store.delete(IDBValidKey(id)))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private fun indexedDb(): IDBFactory =
        if ((js("typeof indexedDB === 'undefined'") as Boolean)) {
            fail("indexedDB unavailable")
        } else {
            indexedDB
        }

    private suspend fun clearDatabase() {
        val factory = indexedDb()
        suspendOnOpenRequest(factory.deleteDatabase(postCacheDatabaseName))
        suspendOnOpenRequest(factory.deleteDatabase(postCacheTopicsDatabaseName))
    }

    private suspend fun suspendOnOpenRequest(request: IDBOpenDBRequest) {
        suspendCancellableCoroutine { continuation ->
            val dynamicRequest = request.asDynamic()
            val resume: () -> Unit = { continuation.resume(Unit) }
            dynamicRequest.onsuccess = resume
            dynamicRequest.onerror = resume
            dynamicRequest.onblocked = resume
        }
    }

    private fun runCacheTest(
        maxEntries: Int = 10_000,
        block: suspend (IndexedDbPostCache, RecordingLogger) -> Unit,
    ) = runTest {
        clearDatabase()
        val logger = RecordingLogger()
        val entryStore = TopicEntryStore(indexedDb(), asyncBridge, maxEntries)
        val referenceStore = TopicReferenceStore(indexedDb(), asyncBridge, logger)
        val cache = IndexedDbPostCache(entryStore, referenceStore, logger)
        try {
            block(cache, logger)
            assertTrue(logger.errorMessages.isEmpty(), "Cache errors: ${logger.errorMessages}")
            assertTrue(logger.warningMessages.isEmpty(), "Cache warnings: ${logger.warningMessages}")
        } finally {
            clearDatabase()
        }
    }

    private suspend fun storedEntryIds(): List<String> {
        val factory = indexedDb()
        val database = asyncBridge.awaitRequest<IDBDatabase>(factory.open(postCacheDatabaseName))
        try {
            if (!database.objectStoreNames.contains(postCacheEntriesStoreName)) return emptyList()
            val transaction = database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(postCacheEntriesStoreName)
            val records = asyncBridge.awaitRequest<Array<dynamic>>(store.getAll())
            asyncBridge.awaitTransaction(transaction)
            return records.mapNotNull { entry -> entry?.id as? String }
        } finally {
            database.close()
        }
    }

    private suspend fun storedTopicRecord(topic: String): dynamic {
        val factory = indexedDb()
        val database = asyncBridge.awaitRequest<IDBDatabase>(factory.open(postCacheTopicsDatabaseName))
        try {
            if (!database.objectStoreNames.contains(postCacheTopicsStoreName)) return null
            val transaction = database.transaction(postCacheTopicsStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(postCacheTopicsStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(IDBValidKey(topic)))
            asyncBridge.awaitTransaction(transaction)
            return record
        } finally {
            database.close()
        }
    }

    private suspend fun storedTopicKeys(): List<String> {
        val factory = indexedDb()
        val database = asyncBridge.awaitRequest<IDBDatabase>(factory.open(postCacheTopicsDatabaseName))
        try {
            if (!database.objectStoreNames.contains(postCacheTopicsStoreName)) return emptyList()
            val transaction = database.transaction(postCacheTopicsStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(postCacheTopicsStoreName)
            val records = asyncBridge.awaitRequest<Array<dynamic>>(store.getAll())
            asyncBridge.awaitTransaction(transaction)
            return records.mapNotNull { entry -> entry?.topic as? String }
        } finally {
            database.close()
        }
    }

    private class RecordingLogger : Logger {
        val errorMessages = mutableListOf<String>()
        val warningMessages = mutableListOf<String>()
        override fun error(message: String, throwable: Throwable?) {
            val suffix = throwable?.message?.let { ": $it" } ?: ""
            errorMessages += message + suffix
        }

        override fun warn(message: String, throwable: Throwable?) {
            val suffix = throwable?.message?.let { ": $it" } ?: ""
            warningMessages += message + suffix
        }
    }

}
