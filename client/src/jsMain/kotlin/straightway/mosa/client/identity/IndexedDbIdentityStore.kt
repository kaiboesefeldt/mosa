package straightway.mosa.client.identity

import kotlin.js.ExperimentalWasmJsInterop
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
import web.idb.IDBTransactionMode
import web.idb.IDBValidKey
import web.idb.readonly
import web.idb.readwrite

@OptIn(ExperimentalWasmJsInterop::class)
class IndexedDbIdentityStore(
    private val logger: Logger,
    private val schemaManager: IndexedDbSchemaManager,
    private val recordMapper: IdentityRecordMapper,
    private val asyncBridge: IndexedDbAsyncBridge,
) : IdentityStore {
    override suspend fun loadState(): IdentityStoreState? =
        runCatching { loadInternal() }
            .getOrElseLogOrThrowCancellation(logger, "IndexedDB identity load failed") { null }

    override suspend fun storeState(state: IdentityStoreState) {
        runCatching { storeInternal(state) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB identity store failed")
    }

    private suspend fun loadInternal(): IdentityStoreState? {
        val database = schemaManager.openDatabase(asyncBridge) ?: return null
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            val record = asyncBridge.awaitRequest<dynamic?>(store.get(validSingletonKey))
            asyncBridge.awaitTransaction(transaction)
            return recordMapper.toState(record)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(state: IdentityStoreState) {
        val payload = recordMapper.toRecord(state)
        val database = schemaManager.openDatabase(asyncBridge) ?: return
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readwrite)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            asyncBridge.awaitRequest<Any?>(store.put(payload, validSingletonKey))
            asyncBridge.awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val singletonKey = "instances"
        val validSingletonKey: IDBValidKey = IDBValidKey(singletonKey)
    }
}
