package straightway.mosa.client.topic

import kotlin.js.ExperimentalWasmJsInterop
import straightway.mosa.client.identity.IndexedDbAsyncBridge
import straightway.mosa.client.identity.IndexedDbSchemaManager
import straightway.mosa.client.logging.Logger
import web.idb.IDBFactory
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

@OptIn(ExperimentalWasmJsInterop::class)
class IndexedDbTopicStore(
    indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val logger: Logger,
) : TopicStore {
    private val schemaManager =
        IndexedDbSchemaManager(
            indexedDb,
            IndexedDbSchemaManager.Schema(
                databaseName = "mosa-topics",
                objectStoreName = "topics",
                schemaVersion = 1,
            ),
        )

    override suspend fun load(): List<Topic> =
        runCatching { loadInternal() }
            .getOrElse {
                logger.warn("IndexedDB topic load failed", it)
                emptyList()
            }

    override suspend fun save(topics: List<Topic>) {
        runCatching { storeInternal(topics) }
            .onFailure { logger.warn("IndexedDB topic store failed", it) }
    }

    private suspend fun loadInternal(): List<Topic> {
        val database = schemaManager.openDatabase(asyncBridge) ?: return emptyList()
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(validKey))
            asyncBridge.awaitTransaction(transaction)
            return parseTopics(record)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(topics: List<Topic>) {
        val payload = topics.map { it.title }.toTypedArray()
        val database = schemaManager.openDatabase(asyncBridge) ?: return
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            asyncBridge.awaitRequest<Any?>(store.put(payload, validKey))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private fun parseTopics(record: dynamic): List<Topic> {
        if (record == null) return emptyList()
        val length = (record.length as? Number)?.toInt() ?: return emptyList()
        if (length <= 0) return emptyList()
        val topics = mutableListOf<Topic>()
        for (index in 0 until length) {
            val value = record[index] as? String ?: continue
            topics += Topic(value)
        }
        return topics.toList()
    }

    private companion object {
        const val singletonKey = "topics"
        val validKey: IDBValidKey = IDBValidKey(singletonKey)
    }
}
