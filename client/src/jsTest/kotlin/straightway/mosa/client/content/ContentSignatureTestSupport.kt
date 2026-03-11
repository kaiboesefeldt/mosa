package straightway.mosa.client.content

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise
import kotlin.js.unsafeCast
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignaturePayload
import straightway.mosa.content.ContentSignatureVersion
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryResult

internal class FakeVerifierSubtleCrypto {
    private val digests = mutableMapOf<List<Int>, ByteArray>()
    private val signatures = mutableMapOf<Pair<List<Int>, List<Int>>, ByteArray>()
    val wrapped =
        run {
            val subtle = js("{}").unsafeCast<dynamic>()
            subtle.digest = { _: String, data: ByteArray ->
                resolved((digests[data.key()] ?: error("no digest registered")).toArrayBuffer())
            }
            subtle.importKey = { _: String, keyData: ByteArray, _: dynamic, _: Boolean, _: Array<String> ->
                val key = js("{}").unsafeCast<dynamic>()
                key.raw = keyData
                resolved(key.unsafeCast<CryptoKey>())
            }
            subtle.verify = { _: String, key: CryptoKey, signature: ByteArray, data: ByteArray ->
                val raw = key.asDynamic().raw.unsafeCast<ByteArray>()
                resolved(signatures[raw.key() to data.key()]?.contentEquals(signature) == true)
            }
            subtle
        }

    val subtleCrypto = SubtleCrypto(wrapped)

    fun validFetchEntry(
        id: String,
        content: String,
        storedAt: Long = 0,
        rootId: String? = null,
        publicKeyBytes: ByteArray = byteArrayOf(1, 2, 3, id.length.toByte()),
    ): FetchResult.Entry {
        val payload = payloadFor(content, rootId)
        val authorGid = registerGid(publicKeyBytes)
        val authorSignature = registerSignature(publicKeyBytes, payload.toCanonicalBytes())
        return FetchResult.Entry(
            id = id,
            content = content,
            authorGID = authorGid,
            storedAt = storedAt,
            rootId = rootId,
            authorPublicKey = publicKeyBytes.toBase64Url(),
            authorSignature = authorSignature,
            signatureVersion = ContentSignatureVersion.V1,
        )
    }

    fun validQueryEntry(
        id: String,
        content: String,
        similarity: Float,
        storedAt: Long = 0,
        rootId: String? = null,
        publicKeyBytes: ByteArray = byteArrayOf(1, 2, 3, id.length.toByte()),
    ): QueryResult.Entry {
        val entry = validFetchEntry(id, content, storedAt, rootId, publicKeyBytes)
        return QueryResult.Entry(
            id = entry.id,
            content = entry.content,
            similarity = similarity,
            authorGID = entry.authorGID,
            storedAt = entry.storedAt,
            rootId = entry.rootId,
            authorPublicKey = entry.authorPublicKey,
            authorSignature = entry.authorSignature,
            signatureVersion = entry.signatureVersion,
        )
    }

    private fun registerGid(publicKeyBytes: ByteArray): String {
        val digestBytes = "gid:${publicKeyBytes.toBase64Url()}".encodeToByteArray()
        digests[publicKeyBytes.key()] = digestBytes
        return digestBytes.toBase64Url()
    }

    private fun registerSignature(
        publicKeyBytes: ByteArray,
        payloadBytes: ByteArray,
    ): String {
        val signatureBytes = "sig:${publicKeyBytes.toBase64Url()}:${payloadBytes.toBase64Url()}".encodeToByteArray()
        signatures[publicKeyBytes.key() to payloadBytes.key()] = signatureBytes
        return signatureBytes.toBase64Url()
    }

    private fun payloadFor(
        content: String,
        rootId: String?,
    ): ContentSignaturePayload =
        if (rootId == null) {
            ContentSignaturePayload.forPost(content)
        } else {
            ContentSignaturePayload.forReply(content, rootId)
        }
}

private fun ByteArray.key(): List<Int> = map { it.toInt() and 0xFF }

private fun <T> resolved(value: T): Promise<T> = Promise { resolve, _ -> resolve(value) }

private fun ByteArray.toArrayBuffer(): ArrayBuffer =
    ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }
