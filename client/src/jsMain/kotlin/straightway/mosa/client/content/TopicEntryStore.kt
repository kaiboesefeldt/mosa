package straightway.mosa.client.content

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.asDynamic
import kotlin.js.js
import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast
import kotlin.math.max
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

@OptIn(ExperimentalWasmJsInterop::class)
class TopicEntryStore(
    private val indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val maxEntries: Int,
) {
    suspend fun store(entries: List<CachedPost>): Boolean {
        val database = openDatabase() ?: return false
        try {
            val deduplicated = entries.associateBy { it.id }.values
            deduplicated.forEach { entry ->
                storeEntry(database, entry)
                enforceEntryLimit(database)
            }
            return true
        } finally {
            database.close()
        }
    }

    suspend fun resolve(references: List<TopicEntryRef>): EntryResolution {
        if (references.isEmpty()) return EntryResolution(emptyList(), emptyList())
        val database = openDatabase() ?: return EntryResolution(emptyList(), references.map { it.id })
        val resolved = mutableListOf<CachedPost>()
        val missing = mutableListOf<String>()
        try {
            val transaction =
                database.transaction(POST_CACHE_ENTRIES_STORE_NAME, IDBTransactionMode.readonly)
            val store = transaction.objectStore(POST_CACHE_ENTRIES_STORE_NAME)
            references.forEach { ref ->
                val payload = asyncBridge.awaitRequest<dynamic?>(store.get(entryKey(ref.id)))
                val decoded = decodeEntry(payload)
                if (decoded == null) {
                    missing += ref.id
                } else {
                    resolved += decoded
                }
            }
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
        return EntryResolution(resolved, missing)
    }

    private suspend fun storeEntry(
        database: IDBDatabase,
        entry: CachedPost,
    ) {
        while (true) {
            val transaction =
                database.transaction(POST_CACHE_ENTRIES_STORE_NAME, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(POST_CACHE_ENTRIES_STORE_NAME)
            val request = store.put(encodeEntry(entry))
            val result =
                runCatching {
                    asyncBridge.awaitRequest<Any?>(request)
                    asyncBridge.awaitTransaction(transaction)
                }
            val failure = result.exceptionOrNull() ?: return
            if (!isQuotaError(failure)) throw failure
            val currentCount = countEntries(database)
            val removalCount = calculateEvictionCount(currentCount)
            removeOldestEntries(database, removalCount)
        }
    }

    private suspend fun enforceEntryLimit(database: IDBDatabase) {
        var currentCount = countEntries(database)
        while (currentCount > maxEntries) {
            val removalCount = calculateEvictionCount(currentCount)
            removeOldestEntries(database, removalCount)
            currentCount = countEntries(database)
        }
    }

    private suspend fun countEntries(database: IDBDatabase): Int {
        val transaction =
            database.transaction(POST_CACHE_ENTRIES_STORE_NAME, IDBTransactionMode.readonly)
        val store = transaction.objectStore(POST_CACHE_ENTRIES_STORE_NAME)
        val count = asyncBridge.awaitRequest<Double>(store.count()).toInt()
        asyncBridge.awaitTransaction(transaction)
        return count
    }

    private suspend fun removeOldestEntries(
        database: IDBDatabase,
        limit: Int,
    ) {
        if (limit <= 0) return
        val transaction =
            database.transaction(POST_CACHE_ENTRIES_STORE_NAME, IDBTransactionMode.readwrite)
        val store = transaction.objectStore(POST_CACHE_ENTRIES_STORE_NAME)
        val index = store.index(POST_CACHE_STORED_AT_INDEX_NAME)
        val keys = asyncBridge.awaitRequest<Array<IDBValidKey>>(index.getAllKeys(count = limit))
        keys.forEach { key -> asyncBridge.awaitRequest<Any?>(store.delete(key)) }
        asyncBridge.awaitTransaction(transaction)
    }

    private fun calculateEvictionCount(currentCount: Int): Int =
        max(1, (currentCount * POST_CACHE_EVICTION_RATIO).toInt())

    private suspend fun openDatabase(): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(POST_CACHE_DATABASE_NAME, POST_CACHE_SCHEMA_VERSION.toDouble())
        request.asDynamic().onupgradeneeded = {
            val db = request.result
            if (!db.objectStoreNames.contains(POST_CACHE_ENTRIES_STORE_NAME)) {
                val entriesStore =
                    db.createObjectStore(
                        POST_CACHE_ENTRIES_STORE_NAME,
                        js("{ keyPath: 'id' }"),
                    )
                entriesStore.createIndex(POST_CACHE_STORED_AT_INDEX_NAME, "storedAt")
            }
        }
        val database = asyncBridge.awaitRequest<IDBDatabase>(request)
        if (!database.objectStoreNames.contains(POST_CACHE_ENTRIES_STORE_NAME)) {
            database.close()
            asyncBridge.awaitRequest<Any?>(factory.deleteDatabase(POST_CACHE_DATABASE_NAME))
            return openDatabase()
        }
        return database
    }

    private fun encodeEntry(entry: CachedPost): dynamic {
        val payload = js("{}")
        payload.id = entry.id
        payload.content = entry.content
        payload.authorGID = entry.authorGID
        payload.storedAt = entry.storedAt.toDouble()
        entry.rootId?.let { payload.rootId = it }
        return payload
    }

    private fun decodeEntry(payload: dynamic): CachedPost? {
        if (payload == null || jsTypeOf(payload) == "undefined") return null
        val id = payload.id as? String ?: return null
        val content = payload.content as? String ?: return null
        val author = payload.authorGID as? String ?: return null
        val storedAt = (payload.storedAt as? Number)?.toLong() ?: return null
        val rootId = payload.rootId as? String
        return CachedPost(id, content, author, storedAt, rootId)
    }

    private fun entryKey(value: String): IDBValidKey = value.unsafeCast<IDBValidKey>()

    private fun isQuotaError(throwable: Throwable): Boolean {
        val dynamic = throwable.asDynamic()
        val name = dynamic?.name as? String
        return name == "QuotaExceededError"
    }

    companion object {
        internal const val POST_CACHE_DATABASE_NAME = "mosa-topic-post-cache"
        internal const val POST_CACHE_ENTRIES_STORE_NAME = "topic-post-cache-entries"
        internal const val POST_CACHE_STORED_AT_INDEX_NAME = "stored-at-index"
        internal const val POST_CACHE_SCHEMA_VERSION: Short = 1
        internal const val POST_CACHE_EVICTION_RATIO = 0.05

    }
}

