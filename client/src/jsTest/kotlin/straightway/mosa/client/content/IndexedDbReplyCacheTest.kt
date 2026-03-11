package straightway.mosa.client.content

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.content.TopicEntryStore.Companion.POST_CACHE_DATABASE_NAME
import straightway.mosa.client.content.TopicEntryStore.Companion.POST_CACHE_ENTRIES_STORE_NAME
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.coroutines.resume
import kotlin.js.asDynamic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBOpenDBRequest
import web.idb.IDBTransactionMode
import web.idb.indexedDB
import web.idb.readonly

class IndexedDbReplyCacheTest {
    private val asyncBridge = IndexedDbAsyncBridge()

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `evict removes post from all roots and entry store`() =
        runCacheTest { cache, _, _ ->
            cache.append("root-1", listOf(entry("gone", 10), entry("stay", 15)))
            cache.append("root-2", listOf(entry("gone", 12)))

            cache.evict("gone")

            assertEquals(listOf("stay"), cache.snapshot("root-1").entries.map { it.post.id })
            assertTrue(cache.snapshot("root-2").entries.isEmpty())
            assertEquals(listOf("stay"), storedEntryIds().sorted())
        }

    private fun entry(id: String, storedAt: Long): ThreadScopedReply =
        ThreadScopedReply(
            CachedPost(
                id = id,
                content = "content-$id",
                authorGID = "author",
                storedAt = storedAt,
                rootId = "root",
                authorPublicKey = "pub-$id",
                authorSignature = "sig-$id",
                signatureVersion = 1,
            ),
        )

    private fun indexedDb(): IDBFactory =
        if ((js("typeof indexedDB === 'undefined'") as Boolean)) {
            fail("indexedDB unavailable")
        } else {
            indexedDB
        }

    private suspend fun clearDatabase() {
        val factory = indexedDb()
        suspendOnOpenRequest(factory.deleteDatabase(POST_CACHE_DATABASE_NAME))
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
        block: suspend (IndexedDbReplyCache, RecordingLogger, TopicEntryStore) -> Unit,
    ) = runTest {
        clearDatabase()
        val logger = RecordingLogger()
        val entryStore = TopicEntryStore(indexedDb(), asyncBridge, maxEntries)
        val referenceStore = ReplyReferenceStore()
        val cache = IndexedDbReplyCache(entryStore, referenceStore, logger)
        try {
            block(cache, logger, entryStore)
            assertTrue(logger.errorMessages.isEmpty(), "Cache errors: ${logger.errorMessages}")
            assertTrue(logger.warningMessages.isEmpty(), "Cache warnings: ${logger.warningMessages}")
        } finally {
            clearDatabase()
        }
    }

    private suspend fun storedEntryIds(): List<String> {
        val factory = indexedDb()
        val database = asyncBridge.awaitRequest<IDBDatabase>(factory.open(POST_CACHE_DATABASE_NAME))
        try {
            if (!database.objectStoreNames.contains(POST_CACHE_ENTRIES_STORE_NAME)) return emptyList()
            val transaction = database.transaction(POST_CACHE_ENTRIES_STORE_NAME, IDBTransactionMode.readonly)
            val store = transaction.objectStore(POST_CACHE_ENTRIES_STORE_NAME)
            val records = asyncBridge.awaitRequest<Array<dynamic>>(store.getAll())
            asyncBridge.awaitTransaction(transaction)
            return records.mapNotNull { entry -> entry?.id as? String }
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
