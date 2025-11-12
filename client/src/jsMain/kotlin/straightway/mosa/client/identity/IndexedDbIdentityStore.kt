package straightway.mosa.client.identity

import kotlinx.coroutines.suspendCancellableCoroutine
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.KeyAlgorithm
import straightway.mosa.client.browser.KeyUsage
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.logging.Logger
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
    private val logger: Logger,
) : IdentityStore {
    override suspend fun loadState(): IdentityStoreState? =
        runCatching { loadInternal() }
            .getOrElse {
                logger.warn("IndexedDB identity load failed", it)
                null
            }

    override suspend fun storeState(state: IdentityStoreState) {
        runCatching { storeInternal(state) }
            .onFailure { logger.warn("IndexedDB identity store failed", it) }
    }

    private suspend fun loadInternal(): IdentityStoreState? {
        val database = openDatabase() ?: return null
        try {
            val transaction = database.transaction(arrayOf(objectStoreName), "readonly")
            val store = transaction.objectStore(objectStoreName)
            val record = awaitRequest(store.get(singletonKey))
            awaitTransaction(transaction)
            if (isNullish(record)) return null
            return parseRecord(record)
        } finally {
            database.close()
        }
    }

    private suspend fun storeInternal(state: IdentityStoreState) {
        val entries = state.identities.map { identity ->
            val publicKey = cryptoExport.pubRawOf(identity.keyPair)
            val privateKey = cryptoExport.privPkcs8Of(identity.keyPair)
            json(
                "gid" to identity.gid,
                "publicKey" to publicKey.toBase64Url(),
                "privateKey" to privateKey.toBase64Url(),
            )
        }.toTypedArray()
        val payload =
            json(
                "activeIndex" to state.activeIndex,
                "identities" to entries,
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

    private fun isNullish(value: dynamic): Boolean =
        value == null || jsTypeOf(value) == "undefined"

    private suspend fun parseRecord(record: dynamic): IdentityStoreState? {
        val identitiesField = record.identities
        if (isNullish(identitiesField)) return null
        val parsed = parseIdentitiesArray(identitiesField) ?: return null
        val indexValue = record.activeIndex
        val resolvedIndex =
            when (indexValue) {
                is Number -> indexValue.toInt()
                else -> 0
            }.coerceIn(0, parsed.lastIndex)
        return IdentityStoreState(parsed, resolvedIndex)
    }

    private suspend fun parseIdentitiesArray(field: dynamic): List<Identity>? {
        val length =
            when (val lengthValue = field.length) {
                is Number -> lengthValue.toInt()
                else -> return null
            }
        if (length <= 0) return null
        val identities = mutableListOf<Identity>()
        for (index in 0 until length) {
            val entry = field[index]
            val parsed = parseIdentity(entry) ?: return null
            identities.add(parsed)
        }
        return identities.toList()
    }

    private suspend fun parseIdentity(stored: dynamic): Identity? {
        val gid = stored.gid as? String ?: return null
        val publicKeyField = stored.publicKey as? String ?: return null
        val privateKeyField = stored.privateKey as? String ?: return null
        val publicKeyBytes = Base64.fromBase64Url(publicKeyField)
        val privateKeyBytes = Base64.fromBase64Url(privateKeyField)
        val algorithm = KeyAlgorithm.Ed25519

        val publicKey = subtleCrypto.importKey("raw", publicKeyBytes, algorithm, true, arrayOf(KeyUsage.Verify))
        val privateKey = subtleCrypto.importKey("pkcs8", privateKeyBytes, algorithm, true, arrayOf(KeyUsage.Sign))
        val keyPair =
            json(
                "publicKey" to publicKey,
                "privateKey" to privateKey,
            ).unsafeCast<CryptoKeyPair>()

        return Identity(gid, keyPair)
    }

    private companion object {
        const val databaseName = "mosa-identity"
        const val objectStoreName = "identity"
        const val singletonKey = "instances"
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
