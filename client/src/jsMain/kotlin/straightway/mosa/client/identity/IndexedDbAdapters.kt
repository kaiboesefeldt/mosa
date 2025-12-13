package straightway.mosa.client.identity

import kotlinx.coroutines.suspendCancellableCoroutine
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.asDynamic
import kotlin.js.jsTypeOf
import kotlin.js.json
import kotlin.js.unsafeCast
import web.idb.IDBDatabase
import web.idb.IDBFactory
import web.idb.IDBRequest
import web.idb.IDBTransaction

class IndexedDbAsyncBridge {
    suspend fun <T> awaitRequest(request: IDBRequest<*>): T =
        suspendCancellableCoroutine { continuation ->
            val dynamicRequest = request.asDynamic()
            dynamicRequest.onsuccess = {
                continuation.resume(request.result.unsafeCast<T>())
            }
            dynamicRequest.onerror = {
                continuation.resumeWithException(toThrowable(request.error, "IndexedDB request error"))
            }
        }

    suspend fun awaitTransaction(transaction: IDBTransaction) =
        suspendCancellableCoroutine { continuation ->
            val dynamicTransaction = transaction.asDynamic()
            dynamicTransaction.oncomplete = {
                continuation.resume(Unit)
            }
            val fail: () -> Unit = {
                continuation.resumeWithException(
                    toThrowable(transaction.error, "IndexedDB transaction error")
                )
            }
            dynamicTransaction.onerror = fail
            dynamicTransaction.onabort = fail
        }

    private fun toThrowable(error: Any?, fallbackMessage: String): Throwable =
        when (error) {
            is Throwable -> error
            null -> RuntimeException(fallbackMessage)
            else -> RuntimeException(error.toString())
        }
}

class IndexedDbSchemaManager(
    private val indexedDb: IDBFactory?,
    private val schema: Schema = Schema(),
) {
    data class Schema(
        val databaseName: String = "mosa-identity",
        val objectStoreName: String = "identity",
        val schemaVersion: Short = 2,
    )

    val objectStoreName: String
        get() = schema.objectStoreName

    suspend fun openDatabase(asyncBridge: IndexedDbAsyncBridge): IDBDatabase? {
        val factory = indexedDb ?: return null
        val request = factory.open(schema.databaseName, schema.schemaVersion.toDouble())
        request.asDynamic().onupgradeneeded = {
            val db = request.result
            if (db.objectStoreNames.contains(schema.objectStoreName)) {
                db.deleteObjectStore(schema.objectStoreName)
            }
            db.createObjectStore(schema.objectStoreName)
        }
        return asyncBridge.awaitRequest(request)
    }
}

class IdentityRecordMapper {
    fun toState(record: dynamic): IdentityStoreState? {
        if (isNullish(record)) return null
        val identitiesField = record.identities
        val parsed = parseIdentities(identitiesField) ?: return null
        val resolvedIndex = resolveIndex(record.activeIndex, parsed.lastIndex)
        return IdentityStoreState(parsed, resolvedIndex)
    }

    fun toRecord(state: IdentityStoreState): dynamic {
        val entries = state.identities.map { identity ->
            val protectedExport = identity.protectedExport ?: error("Identity lacks protected export")
            json(
                "gid" to identity.gid,
                "protectedExport" to protectedExport,
                "publicKey" to identity.keyPair.publicKey,
                "privateKey" to identity.keyPair.privateKey,
            )
        }.toTypedArray()
        return json(
            "activeIndex" to state.activeIndex,
            "identities" to entries,
        )
    }

    private fun parseIdentities(field: dynamic): List<Identity>? {
        if (isNullish(field)) return null
        val length = (field.length as? Number)?.toInt() ?: return null
        if (length <= 0) return null
        val identities = mutableListOf<Identity>()
        for (index in 0 until length) {
            val entry = field[index]
            val parsed = parseIdentity(entry) ?: return null
            identities.add(parsed)
        }
        return identities.toList()
    }

    private fun parseIdentity(entry: dynamic): Identity? {
        val gid = entry.gid as? String ?: return null
        val protectedExport = entry.protectedExport as? String ?: return null
        val publicKeyDynamic = entry.publicKey ?: return null
        val privateKeyDynamic = entry.privateKey ?: return null
        val storedPublicKey = publicKeyDynamic.unsafeCast<CryptoKey>()
        val storedPrivateKey = privateKeyDynamic.unsafeCast<CryptoKey>()
        val keyPair =
            json(
                "publicKey" to storedPublicKey,
                "privateKey" to storedPrivateKey,
            ).unsafeCast<CryptoKeyPair>()
        return Identity(gid, keyPair, protectedExport)
    }

    private fun resolveIndex(indexValue: Any?, lastIndex: Int): Int =
        when (indexValue) {
            is Number -> indexValue.toInt()
            else -> 0
        }.coerceIn(0, lastIndex)

    private fun isNullish(value: dynamic): Boolean =
        value == null || jsTypeOf(value) == "undefined"
}
