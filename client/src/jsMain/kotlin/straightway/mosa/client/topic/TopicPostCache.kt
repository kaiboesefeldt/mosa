package straightway.mosa.client.topic

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlin.js.asDynamic
import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast
import kotlin.math.max
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
import straightway.mosa.content.QueryResult
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

internal const val topicPostCacheDatabaseName = "mosa-topic-post-cache"
internal const val topicPostTopicsDatabaseName = "mosa-topic-post-cache-topics"
internal const val topicPostEntriesStoreName = "topic-post-cache-entries"
internal const val topicPostTopicsStoreName = "topic-post-cache-topics"
internal const val topicPostStoredAtIndexName = "stored-at-index"

data class TopicPostCacheSnapshot(
    val entries: List<QueryResult.Entry>,
    val lastFetchedAt: Long?,
)

interface TopicPostCache {
    suspend fun snapshot(topic: String): TopicPostCacheSnapshot
    suspend fun append(topic: String, entries: List<QueryResult.Entry>): TopicPostCacheSnapshot
    suspend fun clear(topic: String)
}

@OptIn(ExperimentalWasmJsInterop::class)
class IndexedDbTopicPostCache(
    private val indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val logger: Logger,
    private val maxEntries: Int = 10_000,
) : TopicPostCache {
    override suspend fun snapshot(topic: String): TopicPostCacheSnapshot =
        runCatching { snapshotInternal(topic) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache snapshot failed for $topic",
            ) {
                TopicPostCacheSnapshot(emptyList(), null)
            }

    override suspend fun append(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): TopicPostCacheSnapshot =
        runCatching { appendInternal(topic, entries) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache append failed for $topic",
            ) {
                fallbackSnapshot(entries)
            }

    override suspend fun clear(topic: String) {
        runCatching { clearInternal(topic) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB topic cache clear failed for $topic")
    }

    private suspend fun snapshotInternal(topic: String): TopicPostCacheSnapshot =
        loadSnapshot(topic)

    private suspend fun appendInternal(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): TopicPostCacheSnapshot {
        if (entries.isEmpty()) return snapshotInternal(topic)
        val entriesDatabase = openEntriesDatabase() ?: return fallbackSnapshot(entries)
        val persistable = entries.filter { it.id.isNotBlank() }
        if (persistable.isEmpty()) {
            entriesDatabase.close()
            return loadSnapshot(topic)
        }
        try {
            storeEntries(entriesDatabase, persistable)
        } finally {
            entriesDatabase.close()
        }
        updateTopicRecord(topic, persistable)
        return loadSnapshot(topic)
    }

    private suspend fun clearInternal(topic: String) {
        val database = openTopicsDatabase() ?: return
        try {
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            asyncBridge.awaitRequest<Any?>(store.delete(topicKey(topic)))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private fun fallbackSnapshot(entries: List<QueryResult.Entry>): TopicPostCacheSnapshot {
        if (entries.isEmpty()) return TopicPostCacheSnapshot(emptyList(), null)
        val deduped =
            entries
                .associateBy { it.id }
                .values
                .sortedByDescending { it.similarity }
        val lastFetchedAt = entries.maxOfOrNull { it.storedAt }
        return TopicPostCacheSnapshot(deduped, lastFetchedAt)
    }

    private suspend fun loadSnapshot(topic: String): TopicPostCacheSnapshot {
        val topicRecord = loadTopicRecord(topic) ?: return TopicPostCacheSnapshot(emptyList(), null)
        val entriesDatabase = openEntriesDatabase() ?: return TopicPostCacheSnapshot(emptyList(), topicRecord.lastFetchedAt)
        val resolved = mutableListOf<QueryResult.Entry>()
        val missing = mutableListOf<String>()
        try {
            val transaction =
                entriesDatabase.transaction(topicPostEntriesStoreName, IDBTransactionMode.readonly)
            val entryStore = transaction.objectStore(topicPostEntriesStoreName)
            for (ref in topicRecord.entries) {
                val entryPayload = asyncBridge.awaitRequest<dynamic?>(entryStore.get(entryKey(ref.id)))
                val decoded = decodeEntry(entryPayload, ref.similarity)
                if (decoded == null) {
                    missing += ref.id
                } else {
                    resolved += decoded
                }
            }
            asyncBridge.awaitTransaction(transaction)
        } finally {
            entriesDatabase.close()
        }
        if (missing.isNotEmpty()) {
            removeMissingReferences(topic, missing)
        }
        val sorted = resolved.sortedByDescending { it.similarity }
        return TopicPostCacheSnapshot(sorted, topicRecord.lastFetchedAt)
    }

    private suspend fun loadTopicRecord(topic: String): TopicRecord? {
        val database = openTopicsDatabase() ?: return null
        try {
            if (!database.objectStoreNames.contains(topicPostTopicsStoreName)) {
                logger.warn("Topic cache store unavailable", null)
                return null
            }
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(topicKey(topic))) ?: run {
                asyncBridge.awaitTransaction(transaction)
                return null
            }
            asyncBridge.awaitTransaction(transaction)
            return parseTopicRecord(record)
        } finally {
            database.close()
        }
    }

    private suspend fun updateTopicRecord(
        topic: String,
        entries: List<QueryResult.Entry>,
    ) {
        val database = openTopicsDatabase() ?: return
        try {
            if (!database.objectStoreNames.contains(topicPostTopicsStoreName)) {
                logger.warn("Topic cache store unavailable", null)
                return
            }
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(topicKey(topic)))
            val parsed = parseTopicRecord(record)
            val currentEntries = parsed?.entries?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
            entries.forEach { entry ->
                val similarity = entry.similarity
                if (similarity.isFinite()) {
                    currentEntries[entry.id] = TopicEntryRef(entry.id, similarity)
                }
            }
            val lastFetchedAt =
                entries.maxOfOrNull { it.storedAt }
                    ?: parsed?.lastFetchedAt
            val encoded = encodeTopicRecord(topic, currentEntries.values, lastFetchedAt)
            asyncBridge.awaitRequest<Any?>(store.put(encoded))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private suspend fun storeEntries(
        database: IDBDatabase,
        entries: List<QueryResult.Entry>,
    ) {
        val deduplicated = entries.associateBy { it.id }.values
        deduplicated.forEach { entry ->
            storeEntry(database, entry)
            enforceEntryLimit(database)
        }
    }

    private suspend fun storeEntry(
        database: IDBDatabase,
        entry: QueryResult.Entry,
    ) {
        while (true) {
            val transaction =
                database.transaction(topicPostEntriesStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostEntriesStoreName)
            val request = store.put(encodeEntry(entry))
            val result =
                runCatching {
                    asyncBridge.awaitRequest<Any?>(request)
                    asyncBridge.awaitTransaction(transaction)
                }
            val failure = result.exceptionOrNull()
            if (failure == null) {
                return
            }
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
            database.transaction(topicPostEntriesStoreName, IDBTransactionMode.readonly)
        val store = transaction.objectStore(topicPostEntriesStoreName)
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
            database.transaction(topicPostEntriesStoreName, IDBTransactionMode.readwrite)
        val store = transaction.objectStore(topicPostEntriesStoreName)
        val index = store.index(topicPostStoredAtIndexName)
        val keys = asyncBridge.awaitRequest<Array<IDBValidKey>>(index.getAllKeys(count = limit))
        keys.forEach { key -> asyncBridge.awaitRequest<Any?>(store.delete(key)) }
        asyncBridge.awaitTransaction(transaction)
    }

    private fun calculateEvictionCount(currentCount: Int): Int =
        max(1, (currentCount * evictionRatio).toInt())

    private suspend fun removeMissingReferences(
        topic: String,
        missingIds: List<String>,
    ) {
        val database = openTopicsDatabase() ?: return
        try {
            if (!database.objectStoreNames.contains(topicPostTopicsStoreName)) {
                logger.warn("Topic cache store unavailable", null)
                return
            }
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(topicKey(topic))) ?: run {
                asyncBridge.awaitTransaction(transaction)
                return
            }
            val parsed = parseTopicRecord(record) ?: run {
                asyncBridge.awaitTransaction(transaction)
                return
            }
            val missing = missingIds.toSet()
            val filtered = parsed.entries.filterNot { missing.contains(it.id) }
            val encoded = encodeTopicRecord(topic, filtered, parsed.lastFetchedAt)
            asyncBridge.awaitRequest<Any?>(store.put(encoded))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private suspend fun openEntriesDatabase(): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(topicPostCacheDatabaseName, schemaVersion.toDouble())
        request.asDynamic().onupgradeneeded = {
            val db = request.result
            if (!db.objectStoreNames.contains(topicPostEntriesStoreName)) {
                val entriesStore =
                    db.createObjectStore(
                        topicPostEntriesStoreName,
                        js("{ keyPath: 'id' }"),
                    )
                entriesStore.createIndex(topicPostStoredAtIndexName, "storedAt")
            }
        }
        val database = asyncBridge.awaitRequest<IDBDatabase>(request)
        if (!database.objectStoreNames.contains(topicPostEntriesStoreName)) {
            database.close()
            asyncBridge.awaitRequest<Any?>(factory.deleteDatabase(topicPostCacheDatabaseName))
            return openEntriesDatabase()
        }
        return database
    }

    private suspend fun openTopicsDatabase(): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(topicPostTopicsDatabaseName, schemaVersion.toDouble())
        request.asDynamic().onupgradeneeded = {
            val db = request.result
            if (!db.objectStoreNames.contains(topicPostTopicsStoreName)) {
                db.createObjectStore(
                    topicPostTopicsStoreName,
                    js("{ keyPath: 'topic' }"),
                )
            }
        }
        val database = asyncBridge.awaitRequest<IDBDatabase>(request)
        if (!database.objectStoreNames.contains(topicPostTopicsStoreName)) {
            database.close()
            asyncBridge.awaitRequest<Any?>(factory.deleteDatabase(topicPostTopicsDatabaseName))
            return openTopicsDatabase()
        }
        return database
    }

    private fun encodeEntry(entry: QueryResult.Entry): dynamic {
        val payload = js("{}")
        payload.id = entry.id
        payload.content = entry.content
        payload.authorGID = entry.authorGID
        payload.storedAt = entry.storedAt.toDouble()
        entry.rootId?.let { payload.rootId = it }
        return payload
    }

    private fun decodeEntry(payload: dynamic, similarity: Float): QueryResult.Entry? {
        if (payload == null || jsTypeOf(payload) == "undefined") return null
        val id = payload.id as? String ?: return null
        val content = payload.content as? String ?: return null
        val author = payload.authorGID as? String ?: return null
        val storedAt = (payload.storedAt as? Number)?.toLong() ?: return null
        val rootId = payload.rootId as? String
        return QueryResult.Entry(id, content, similarity, author, storedAt, rootId)
    }

    private fun encodeTopicRecord(
        topic: String,
        entries: Collection<TopicEntryRef>,
        lastFetchedAt: Long?,
    ): dynamic {
        val payload = js("{}")
        payload.topic = topic
        payload.entries =
            entries
                .sortedByDescending { it.similarity }
                .map { ref ->
                    val entry = js("{}")
                    entry.id = ref.id
                    entry.similarity = ref.similarity
                    entry
                }
                .toTypedArray()
        if (lastFetchedAt != null) {
            payload.lastFetchedAt = lastFetchedAt.toDouble()
        }
        return payload
    }

    private fun parseTopicRecord(record: dynamic): TopicRecord? {
        if (record == null || jsTypeOf(record) == "undefined") return null
        val entriesField = record.entries
        val length = (entriesField?.length as? Number)?.toInt() ?: 0
        val entries = mutableListOf<TopicEntryRef>()
        for (index in 0 until length) {
            val entry = entriesField[index]
            val id = entry?.id as? String ?: continue
            val similarityValue = (entry.similarity as? Number)?.toFloat() ?: continue
            entries += TopicEntryRef(id, similarityValue)
        }
        val lastFetchedAt = (record.lastFetchedAt as? Number)?.toLong()
        return TopicRecord(entries, lastFetchedAt)
    }

    private fun isQuotaError(throwable: Throwable): Boolean {
        val dynamic = throwable.asDynamic()
        val name = dynamic?.name as? String
        return name == "QuotaExceededError"
    }

    private data class TopicEntryRef(
        val id: String,
        val similarity: Float,
    )

    private data class TopicRecord(
        val entries: List<TopicEntryRef>,
        val lastFetchedAt: Long?,
    )

    private fun topicKey(value: String): IDBValidKey = value.unsafeCast<IDBValidKey>()

    private fun entryKey(value: String): IDBValidKey = value.unsafeCast<IDBValidKey>()

    private companion object {
        const val schemaVersion: Short = 1
        const val evictionRatio = 0.05
    }
}
