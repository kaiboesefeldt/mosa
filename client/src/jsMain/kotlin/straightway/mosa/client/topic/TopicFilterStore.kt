package straightway.mosa.client.topic

import kotlin.js.JSON
import kotlin.js.json
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.identity.IndexedDbSchemaManager
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
import web.idb.IDBFactory
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

interface TopicFilterStore {
    suspend fun load(): Map<String, String>
    suspend fun save(filters: Map<String, String>)
}

class BrowserTopicFilterStore(
    indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val logger: Logger,
) : TopicFilterStore {
    private val schemaManager =
        IndexedDbSchemaManager(
            indexedDb,
            IndexedDbSchemaManager.Schema(
                databaseName = "mosa-topic-filters",
                objectStoreName = "topic-filters",
                schemaVersion = 1,
            ),
        )
    private var cachedFilters: Map<String, String>? = null
    private var cacheInitialized = false

    override suspend fun load(): Map<String, String> {
        if (!cacheInitialized) {
            cachedFilters =
                runCatching { loadInternal() }
                    .getOrElseLogOrThrowCancellation(logger, "IndexedDB topic filter load failed") {
                        emptyMap()
                    }
            cacheInitialized = true
        }
        return cachedFilters ?: emptyMap()
    }

    override suspend fun save(filters: Map<String, String>) {
        val sanitized = TopicFilterPayload.sanitize(filters)
        cachedFilters = sanitized
        cacheInitialized = true
        runCatching { storeInternal(sanitized) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB topic filter store failed")
    }

    private suspend fun loadInternal(): Map<String, String> {
        val database = schemaManager.openDatabase(asyncBridge) ?: return emptyMap()
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            val raw = asyncBridge.awaitRequest<String?>(store.get(validKey))
            asyncBridge.awaitTransaction(transaction)
            if (raw.isNullOrBlank()) return emptyMap()
            return TopicFilterPayload.parse(raw)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(filters: Map<String, String>) {
        val encoded = TopicFilterPayload.encode(filters)
        val database = schemaManager.openDatabase(asyncBridge) ?: return
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            asyncBridge.awaitRequest<Any?>(store.put(encoded, validKey))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val key = "topic-filters"
        val validKey: IDBValidKey = IDBValidKey(key)
    }
}

internal object TopicFilterPayload {
    fun sanitize(filters: Map<String, String>): Map<String, String> =
        filters
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotEmpty() }

    fun encode(filters: Map<String, String>): String {
        if (filters.isEmpty()) return "{}"
        val entries = filters.map { it.key to it.value }.toTypedArray()
        return JSON.stringify(json(*entries))
    }

    fun parse(raw: String): Map<String, String> {
        val parsed: dynamic = runCatching { JSON.parse<dynamic>(raw) }.getOrNull() ?: return emptyMap()
        val isObject = js("(typeof parsed === 'object') && parsed !== null && !Array.isArray(parsed)") as Boolean
        if (!isObject) return emptyMap()
        val entries = mutableMapOf<String, String>()
        val keys = (js("Object.keys(parsed)") as Array<String>?) ?: emptyArray()
        keys.forEach { title ->
            val value = parsed[title]
            val normalized = (value as? String)?.trim()
            if (!normalized.isNullOrEmpty() && title.isNotBlank()) {
                entries[title] = normalized
            }
        }
        return entries.toMap()
    }
}
