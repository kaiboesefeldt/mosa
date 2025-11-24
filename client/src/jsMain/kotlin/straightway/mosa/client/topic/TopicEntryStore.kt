package straightway.mosa.client.topic

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
internal class TopicEntryStore(
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
                database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(postCacheEntriesStoreName)
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
                database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(postCacheEntriesStoreName)
            val request = store.put(encodeEntry(entry))
            val result =
                runCatching {
                    asyncBridge.awaitRequest<Any?>(request)
                    asyncBridge.awaitTransaction(transaction)
                }
            val failure = result.exceptionOrNull()
            if (failure == null) return
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
            database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readonly)
        val store = transaction.objectStore(postCacheEntriesStoreName)
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
            database.transaction(postCacheEntriesStoreName, IDBTransactionMode.readwrite)
        val store = transaction.objectStore(postCacheEntriesStoreName)
        val index = store.index(postCacheStoredAtIndexName)
        val keys = asyncBridge.awaitRequest<Array<IDBValidKey>>(index.getAllKeys(count = limit))
        keys.forEach { key -> asyncBridge.awaitRequest<Any?>(store.delete(key)) }
        asyncBridge.awaitTransaction(transaction)
    }

    private fun calculateEvictionCount(currentCount: Int): Int =
        max(1, (currentCount * postCacheEvictionRatio).toInt())

    private suspend fun openDatabase(): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(postCacheDatabaseName, postCacheSchemaVersion.toDouble())
        request.asDynamic().onupgradeneeded = {
            val db = request.result
            if (!db.objectStoreNames.contains(postCacheEntriesStoreName)) {
                val entriesStore =
                    db.createObjectStore(
                        postCacheEntriesStoreName,
                        js("{ keyPath: 'id' }"),
                    )
                entriesStore.createIndex(postCacheStoredAtIndexName, "storedAt")
            }
        }
        val database = asyncBridge.awaitRequest<IDBDatabase>(request)
        if (!database.objectStoreNames.contains(postCacheEntriesStoreName)) {
            database.close()
            asyncBridge.awaitRequest<Any?>(factory.deleteDatabase(postCacheDatabaseName))
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
        internal const val postCacheDatabaseName = "mosa-topic-post-cache"
        internal const val postCacheEntriesStoreName = "topic-post-cache-entries"
        internal const val postCacheStoredAtIndexName = "stored-at-index"
        internal const val postCacheSchemaVersion: Short = 1
        internal const val postCacheEvictionRatio = 0.05

    }
}

internal data class EntryResolution(
    val resolved: List<CachedPost>,
    val missingIds: List<String>,
)
