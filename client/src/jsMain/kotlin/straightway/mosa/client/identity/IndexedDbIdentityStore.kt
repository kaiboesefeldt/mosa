package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.json
import kotlin.js.jsTypeOf

class IndexedDbIdentityStore(
    private val subtleCrypto: SubtleCrypto,
    private val cryptoExport: CryptoExport,
    private val indexedDb: dynamic,
) : IdentityStore {
    override suspend fun load(): Identity? =
        runCatching { loadInternal() }
            .getOrElse {
                logWarning("IndexedDB identity load failed", it)
                null
            }

    override suspend fun store(identity: Identity) {
        runCatching { storeInternal(identity) }
            .onFailure { logWarning("IndexedDB identity store failed", it) }
    }

    private suspend fun loadInternal(): Identity? {
        val database = openDatabase() ?: return null
        try {
            val transaction = database.transaction(arrayOf(objectStoreName), "readonly")
            val store = transaction.objectStore(objectStoreName)
            val record = awaitRequest(store.get(singletonKey))
            awaitTransaction(transaction)
            if (isNullish(record)) return null
            val stored = record

            val gid = stored.gid as? String ?: return null
            val publicKeyField = stored.publicKey as? String ?: return null
            val privateKeyField = stored.privateKey as? String ?: return null
            val publicKeyBytes = Base64.fromBase64Url(publicKeyField)
            val privateKeyBytes = Base64.fromBase64Url(privateKeyField)
            val algorithm = js("({ name: 'Ed25519' })")

            val publicKey = subtleCrypto.importKey("raw", publicKeyBytes, algorithm, true, arrayOf("verify"))
            val privateKey = subtleCrypto.importKey("pkcs8", privateKeyBytes, algorithm, true, arrayOf("sign"))
            val keyPair =
                json(
                    "publicKey" to publicKey,
                    "privateKey" to privateKey,
                ).unsafeCast<CryptoKeyPair>()

            return Identity(gid, keyPair)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(identity: Identity) {
        val publicKey = cryptoExport.pubRawOf(identity.keyPair)
        val privateKey = cryptoExport.privPkcs8Of(identity.keyPair)
        val payload =
            json(
                "gid" to identity.gid,
                "publicKey" to publicKey.toBase64Url(),
                "privateKey" to privateKey.toBase64Url(),
            )

        val database = openDatabase() ?: return
        try {
            val transaction = database.transaction(arrayOf(objectStoreName), "readwrite")
            val store = transaction.objectStore(objectStoreName)
            awaitRequest(store.put(payload, singletonKey))
            awaitTransaction(transaction)
        } finally {
            database.close()
        }
    }

    private suspend fun openDatabase(): dynamic {
        val factory = indexedDb ?: return null
        val request = factory.open(databaseName, schemaVersion.toInt())
        request.onupgradeneeded = {
            val db = request.result
            val names = db.objectStoreNames
            var exists = false
            if (names != null) {
                val length = (names.length as? Int) ?: 0
                for (index in 0 until length) {
                    if (names.item(index) == objectStoreName) {
                        exists = true
                        break
                    }
                }
            }
            if (!exists) {
                db.createObjectStore(objectStoreName)
            }
        }
        return awaitRequest(request)
    }

    private fun logWarning(message: String, error: Throwable) {
        window.asDynamic().console?.warn?.call(window.asDynamic().console, message, error)
    }

    private fun isNullish(value: dynamic): Boolean =
        value == null || jsTypeOf(value) == "undefined"

    private companion object {
        const val databaseName = "mosa-identity"
        const val objectStoreName = "identity"
        const val singletonKey = "active"
        const val schemaVersion: Short = 1
    }
}

private suspend fun awaitRequest(request: dynamic): dynamic =
    suspendCancellableCoroutine { continuation ->
        request.onsuccess = {
            continuation.resume(request.result)
        }
        request.onerror = {
            continuation.resumeWithException(toThrowable(request.error, "IndexedDB request error"))
        }
    }

private suspend fun awaitTransaction(transaction: dynamic): Unit =
    suspendCancellableCoroutine { continuation ->
        transaction.oncomplete = {
            continuation.resume(Unit)
        }
        val fail: (dynamic) -> Unit = {
            continuation.resumeWithException(toThrowable(transaction.error, "IndexedDB transaction error"))
        }
        transaction.onerror = fail
        transaction.onabort = fail
    }

private fun toThrowable(error: dynamic, fallbackMessage: String): Throwable =
    when {
        error is Throwable -> error
        error != null -> RuntimeException(error.toString())
        else -> RuntimeException(fallbackMessage)
    }
