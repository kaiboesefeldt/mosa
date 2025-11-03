package straightway.mosa.client.identity

import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64.toBase64Url

class IdentityTransferCodecTest {
    @Test
    fun `encode exports keys as base64 url`() = runTest {
        val publicKeyBytes = byteArrayOf(1, 2, 3)
        val privateKeyBytes = byteArrayOf(4, 5, 6, 7)
        val exportCalls = mutableListOf<ExportKeyCall>()
        val exportWrapper = js("{}")
        exportWrapper.exportKey = { format: String, key: CryptoKey ->
            exportCalls += ExportKeyCall(format, key)
            when (format) {
                "raw" -> resolved(publicKeyBytes.toArrayBuffer())
                "pkcs8" -> resolved(privateKeyBytes.toArrayBuffer())
                else -> error("unexpected format $format")
            }
        }
        val cryptoExport = CryptoExport(SubtleCrypto(exportWrapper))
        val codec = IdentityTransferCodec(
            cryptoExport = cryptoExport,
            subtleCrypto = SubtleCrypto(js("{}")),
            jsonCodec = Json { prettyPrint = false },
        )
        val publicKey = cryptoKey("public-id")
        val privateKey = cryptoKey("private-id")
        val keyPair = js("{}")
        keyPair.publicKey = publicKey
        keyPair.privateKey = privateKey

        val serialized = codec.encode(Identity("gid-123", keyPair.unsafeCast<CryptoKeyPair>()))

        assertEquals(
            listOf(
                ExportKeyCall("raw", publicKey),
                ExportKeyCall("pkcs8", privateKey),
            ),
            exportCalls,
        )
        val decoded = Json.decodeFromString(IdentityTransferPayload.serializer(), serialized)
        assertEquals("gid-123", decoded.gid)
        assertEquals(publicKeyBytes.toBase64Url(), decoded.publicKey)
        assertEquals(privateKeyBytes.toBase64Url(), decoded.privateKey)
    }

    @Test
    fun `decode imports keys and validates gid`() = runTest {
        val publicKeyBytes = byteArrayOf(9, 9, 9)
        val privateKeyBytes = byteArrayOf(5, 4, 3, 2)
        val digestBytes = byteArrayOf(7, 7, 7, 7)
        val expectedGid = digestBytes.toBase64Url()
        val publicKey = cryptoKey("public")
        val privateKey = cryptoKey("private")
        val importCalls = mutableListOf<ImportKeyCall>()
        val subtleWrapped = js("{}")
        subtleWrapped.importKey = { format: String, keyData: ByteArray, _: dynamic, exportable: Boolean, usages: Array<String> ->
            val call = ImportKeyCall(format, keyData.asIntList(), exportable, usages.toList())
            importCalls += call
            when (format) {
                "raw" -> resolved(publicKey)
                "pkcs8" -> resolved(privateKey)
                else -> error("Unexpected format $format")
            }
        }
        subtleWrapped.digest = { _: String, data: ByteArray ->
            assertContentEquals(publicKeyBytes.toList(), data.toList())
            resolved(digestBytes.toArrayBuffer())
        }
        val codec = IdentityTransferCodec(
            cryptoExport = CryptoExport(SubtleCrypto(js("{}"))),
            subtleCrypto = SubtleCrypto(subtleWrapped),
            jsonCodec = Json { prettyPrint = false },
        )
        val payload = IdentityTransferPayload(
            gid = expectedGid,
            publicKey = publicKeyBytes.toBase64Url(),
            privateKey = privateKeyBytes.toBase64Url(),
        )

        val identity = codec.decode(Json.encodeToString(IdentityTransferPayload.serializer(), payload))

        assertEquals(expectedGid, identity.gid)
        assertSame(publicKey, identity.keyPair.publicKey)
        assertSame(privateKey, identity.keyPair.privateKey)
        assertEquals(
            listOf(
                ImportKeyCall("raw", publicKeyBytes.asIntList(), true, listOf("verify")),
                ImportKeyCall("pkcs8", privateKeyBytes.asIntList(), true, listOf("sign")),
            ),
            importCalls,
        )
    }

    @Test
    fun `decode rejects mismatching gid`() = runTest {
        val publicKeyBytes = byteArrayOf(1, 2, 3, 4)
        val digestBytes = byteArrayOf(9, 9, 9)
        val mismatchedGid = byteArrayOf(2, 2, 2).toBase64Url()
        val privateKeyBytes = byteArrayOf(5, 6, 7)
        val subtleWrapped = js("{}")
        subtleWrapped.importKey = { _: String, _: ByteArray, _: dynamic, _: Boolean, _: Array<String> ->
            resolved(cryptoKey("ignored"))
        }
        subtleWrapped.digest = { _: String, _: ByteArray ->
            resolved(digestBytes.toArrayBuffer())
        }
        val codec = IdentityTransferCodec(
            cryptoExport = CryptoExport(SubtleCrypto(js("{}"))),
            subtleCrypto = SubtleCrypto(subtleWrapped),
            jsonCodec = Json { prettyPrint = false },
        )
        val payload = IdentityTransferPayload(
            gid = mismatchedGid,
            publicKey = publicKeyBytes.toBase64Url(),
            privateKey = privateKeyBytes.toBase64Url(),
        )

        assertFailsWith<IllegalArgumentException> {
            codec.decode(Json.encodeToString(IdentityTransferPayload.serializer(), payload))
        }
    }

    private fun resolved(value: dynamic): Promise<dynamic> = Promise { resolve, _ -> resolve(value) }

    private data class ExportKeyCall(val format: String, val key: CryptoKey)

    private data class ImportKeyCall(
        val format: String,
        val keyData: List<Int>,
        val exportable: Boolean,
        val usages: List<String>,
    )

    private fun cryptoKey(id: String): CryptoKey {
        val value = js("{}")
        value.id = id
        return value.unsafeCast<CryptoKey>()
    }

    private fun ByteArray.asIntList(): List<Int> = map { it.toInt() and 0xFF }

    private fun ByteArray.toArrayBuffer(): ArrayBuffer =
        ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }
}
