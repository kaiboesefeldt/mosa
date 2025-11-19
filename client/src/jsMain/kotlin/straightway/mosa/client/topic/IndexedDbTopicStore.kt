package straightway.mosa.client.topic

import kotlin.js.ExperimentalWasmJsInterop
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
            .getOrElseLogOrThrowCancellation(logger, "IndexedDB topic load failed") { emptyList() }

    override suspend fun save(topics: List<Topic>) {
        runCatching { storeInternal(topics) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB topic store failed")
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
        val payload = topics.map { encodeTopic(it) }.toTypedArray()
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
            val entry = record[index]
            val title = (entry.description as? String)?.trim()
            if (title.isNullOrEmpty()) continue
            val filter = (entry.filter as? String)?.trim()?.ifEmpty { null }
            topics += Topic(title, filter)
        }
        return topics.toList()
    }

    private fun encodeTopic(topic: Topic): dynamic {
        val payload = js("{}")
        payload.description = topic.title
        val filter = topic.filter?.trim()
        if (!filter.isNullOrEmpty()) {
            payload.filter = filter
        }
        return payload
    }

    private companion object {
        const val singletonKey = "topics"
        val validKey: IDBValidKey = IDBValidKey(singletonKey)
    }
}
