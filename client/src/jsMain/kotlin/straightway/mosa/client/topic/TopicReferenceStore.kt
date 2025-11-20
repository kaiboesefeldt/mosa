package straightway.mosa.client.topic

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.asDynamic
import kotlin.js.js
import kotlin.js.jsTypeOf
import kotlin.js.unsafeCast
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.logging.Logger
import straightway.mosa.content.QueryResult
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

@OptIn(ExperimentalWasmJsInterop::class)
internal class TopicReferenceStore(
    private val indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val logger: Logger,
) {
    suspend fun load(topic: String): TopicRecord? {
        val database = openDatabase() ?: return null
        try {
            if (!ensureStore(database)) return null
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

    suspend fun update(
        topic: String,
        entries: List<QueryResult.Entry>,
    ) {
        val database = openDatabase() ?: return
        try {
            if (!ensureStore(database)) return
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(topicKey(topic)))
            val parsed = parseTopicRecord(record)
            val currentEntries = parsed?.entries?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
            entries.forEach { entry ->
                if (entry.similarity.isFinite()) {
                    currentEntries[entry.id] = TopicEntryRef(entry.id, entry.similarity)
                }
            }
            val lastFetchedAt = entries.maxOfOrNull { it.storedAt } ?: parsed?.lastFetchedAt
            val encoded = encodeTopicRecord(topic, currentEntries.values, lastFetchedAt)
            asyncBridge.awaitRequest<Any?>(store.put(encoded))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    suspend fun removeMissing(
        topic: String,
        missingIds: Collection<String>,
    ) {
        if (missingIds.isEmpty()) return
        val database = openDatabase() ?: return
        try {
            if (!ensureStore(database)) return
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

    suspend fun clear(topic: String) {
        val database = openDatabase() ?: return
        try {
            if (!ensureStore(database)) return
            val transaction =
                database.transaction(topicPostTopicsStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(topicPostTopicsStoreName)
            asyncBridge.awaitRequest<Any?>(store.delete(topicKey(topic)))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private suspend fun openDatabase(): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(topicPostTopicsDatabaseName, topicPostSchemaVersion.toDouble())
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
            return openDatabase()
        }
        return database
    }

    private fun ensureStore(database: IDBDatabase): Boolean {
        if (!database.objectStoreNames.contains(topicPostTopicsStoreName)) {
            logger.warn("Topic cache store unavailable", null)
            return false
        }
        return true
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

    private fun topicKey(value: String): IDBValidKey = value.unsafeCast<IDBValidKey>()
}

internal data class TopicEntryRef(
    val id: String,
    val similarity: Float,
)

internal data class TopicRecord(
    val entries: List<TopicEntryRef>,
    val lastFetchedAt: Long?,
)
