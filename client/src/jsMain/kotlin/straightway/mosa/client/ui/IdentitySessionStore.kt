package straightway.mosa.client.ui

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

data class IdentitySessionSnapshot(
    val connectedIdentityGid: String?,
    val isProfileVisible: Boolean,
    val selectedTopicTitle: String? = null,
)

interface IdentitySessionStore {
    suspend fun load(): IdentitySessionSnapshot?
    suspend fun save(snapshot: IdentitySessionSnapshot)
}

class BrowserIdentitySessionStore(
    indexedDb: IDBFactory?,
    private val asyncBridge: IndexedDbAsyncBridge,
    private val logger: Logger,
) : IdentitySessionStore {
    private val schemaManager =
        IndexedDbSchemaManager(
            indexedDb,
            IndexedDbSchemaManager.Schema(
                databaseName = "mosa-identity-session",
                objectStoreName = "identity-session",
                schemaVersion = 1,
            ),
        )
    private var cachedSnapshot: IdentitySessionSnapshot? = null
    private var cacheInitialized = false

    override suspend fun load(): IdentitySessionSnapshot? {
        if (!cacheInitialized) {
            cachedSnapshot =
                runCatching { loadInternal() }
                    .getOrElseLogOrThrowCancellation(logger, "IndexedDB identity session load failed") {
                        null
                    }
            cacheInitialized = true
        }
        return cachedSnapshot
    }

    override suspend fun save(snapshot: IdentitySessionSnapshot) {
        cachedSnapshot = snapshot
        cacheInitialized = true
        runCatching { storeInternal(snapshot) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB identity session store failed")
    }

    private suspend fun loadInternal(): IdentitySessionSnapshot? {
        val database = schemaManager.openDatabase(asyncBridge) ?: return null
        try {
            val transaction = database.transaction(schemaManager.objectStoreName, IDBTransactionMode.readonly)
            val store = transaction.objectStore(schemaManager.objectStoreName)
            val raw = asyncBridge.awaitRequest<String?>(store.get(validKey))
            asyncBridge.awaitTransaction(transaction)
            return raw?.let(::parse)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(snapshot: IdentitySessionSnapshot) {
        val encoded = encode(snapshot)
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
        const val key = "identity-session"
        val validKey: IDBValidKey = IDBValidKey(key)

        private fun encode(snapshot: IdentitySessionSnapshot): String =
            JSON.stringify(
                json(
                    "connectedIdentityGid" to snapshot.connectedIdentityGid,
                    "isProfileVisible" to snapshot.isProfileVisible,
                    "selectedTopicTitle" to snapshot.selectedTopicTitle,
                )
            )

        private fun parse(raw: String): IdentitySessionSnapshot? {
            val parsed = runCatching { JSON.parse<dynamic>(raw) }.getOrNull() ?: return null
            val isObject = js("(typeof parsed === 'object') && parsed !== null") as Boolean
            val gid =
                when {
                    parsed is String -> parsed.takeUnless(String::isBlank)
                    isObject -> (parsed.connectedIdentityGid as? String)?.takeUnless { it.isBlank() }
                    else -> null
                }
            val isProfileVisible =
                when {
                    parsed is String -> false
                    isObject -> (parsed.isProfileVisible as? Boolean) ?: false
                    else -> false
                }
            val selectedTopicTitle =
                when {
                    parsed is String -> null
                    isObject -> (parsed.selectedTopicTitle as? String)?.takeUnless(String::isBlank)
                    else -> null
                }
            return IdentitySessionSnapshot(gid, isProfileVisible, selectedTopicTitle)
        }
    }
}
