package straightway.mosa.client.browser

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.also
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import straightway.mosa.client.test.ensureStacktraceMapperInstalled

class SubtleCryptoTest {
    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `sign delegates to wrapped subtle crypto`() = runTest {
        class SignCall(val algo: String, val key: CryptoKey, val bytes: ByteArray)

        val signingBytes = createByteArray(9, 8, 7)
        val signResult = createByteArray(1, 2, 3)
        val privateKey = createKey("signing-key")
        val signCalls = mutableListOf<SignCall>()
        val wrapped = js("({})")
        wrapped.sign = { algo: String, key: CryptoKey, bytes: ByteArray ->
            signCalls += SignCall(algo, key, bytes)
            resolved(signResult.toArrayBuffer())
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.sign("Ed25519", privateKey, signingBytes)

        val call = signCalls.single()
        assertEquals("Ed25519", call.algo)
        assertSame(privateKey, call.key)
        assertSame(signingBytes, call.bytes)
        assertContentEquals(signResult.asIntList(), outcome.asIntList())
    }

    @Test
    fun `exportKey delegates to wrapped subtle crypto`() = runTest {
        data class ExportKeyCall(val format: String, val key: CryptoKey)

        val exportKey = createKey("export-key")
        val exportResult = createByteArray(7, 8, 9, 10)
        val exportCalls = mutableListOf<ExportKeyCall>()
        val wrapped = js("({})")
        wrapped.exportKey = { format: String, key: CryptoKey ->
            exportCalls += ExportKeyCall(format, key)
            resolved(exportResult.toArrayBuffer())
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.exportKey("raw", exportKey)

        val call = exportCalls.single()
        assertEquals("raw", call.format)
        assertSame(exportKey, call.key)
        assertContentEquals(exportResult.asIntList(), outcome.asIntList())
    }

    @Test
    fun `importKey delegates to wrapped subtle crypto`() = runTest {
        data class ImportKeyCall(
            val format: String,
            val keyData: ByteArray,
            val algorithmName: String?,
            val exportable: Boolean,
            val usages: List<String>,
        )

        val importCalls = mutableListOf<ImportKeyCall>()
        val importedKey = createKey("imported")
        val keyBytes = createByteArray(3, 3, 3)
        val wrapped = js("({})")
        wrapped.importKey = { format: String, keyData: ByteArray, algorithm: dynamic, exportable: Boolean, usages: Array<String> ->
            importCalls += ImportKeyCall(format, keyData, algorithm.name as? String, exportable, usages.toList())
            resolved(importedKey)
        }
        val sut = SubtleCrypto(wrapped)

        val key = sut.importKey("raw", keyBytes, KeyAlgorithm.Ed25519, true, arrayOf(KeyUsage.Verify))

        val call = importCalls.single()
        assertEquals("raw", call.format)
        assertSame(keyBytes, call.keyData)
        assertEquals("Ed25519", call.algorithmName)
        assertEquals(true, call.exportable)
        assertEquals(listOf("verify"), call.usages)
        assertSame(importedKey, key)
    }

    @Test
    fun `generateKey delegates to wrapped subtle crypto`() = runTest {
        data class GenerateKeyCall(
            val algorithmName: String?,
            val exportable: Boolean,
            val usages: List<String>,
        )

        val generatedPrivateKey = createKey("generated-private")
        val generatedPublicKey = createKey("generated-public")
        val keyPairResult = js("({})")
        keyPairResult.privateKey = generatedPrivateKey
        keyPairResult.publicKey = generatedPublicKey
        val generateCalls = mutableListOf<GenerateKeyCall>()
        val wrapped = js("({})")
        wrapped.generateKey = { algo: dynamic, exportable: Boolean, usages: Array<String> ->
            generateCalls += GenerateKeyCall(algo.name as? String, exportable, usages.toList())
            resolved(keyPairResult.unsafeCast<CryptoKeyPair>())
        }
        val sut = SubtleCrypto(wrapped)
        val generated = sut.generateKey(KeyAlgorithm.Ed25519, true, arrayOf(KeyUsage.Sign, KeyUsage.Verify))

        val call = generateCalls.single()
        assertEquals("Ed25519", call.algorithmName)
        assertEquals(true, call.exportable)
        assertEquals(listOf("sign", "verify"), call.usages)
        assertSame(generatedPrivateKey, generated.privateKey)
        assertSame(generatedPublicKey, generated.publicKey)
    }

    @Test
    fun `digest delegates to wrapped subtle crypto`() = runTest {
        class DigestCall(val algo: String, val data: ByteArray)
        val digestBytes = createByteArray(3, 4, 5, 6)
        val digestResult = createByteArray(11, 12)
        val digestCalls = mutableListOf<DigestCall>()
        val wrapped = js("({})")
        wrapped.digest = { algo: String, data: ByteArray ->
            digestCalls += DigestCall(algo, data)
            resolved(digestResult.toArrayBuffer())
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.digest("SHA-512", digestBytes)

        val call = digestCalls.single()
        assertEquals("SHA-512", call.algo)
        assertSame(digestBytes, call.data)
        assertContentEquals(digestResult.asIntList(), outcome.asIntList())
    }

    private fun createByteArray(vararg ints: Int): ByteArray =
        ints.map { it.toByte() }.toByteArray()

    private fun createKey(id: String): CryptoKey {
        val key = js("({})")
        key.id = id
        return key.unsafeCast<CryptoKey>()
    }

    private fun <T> resolved(value: T): Promise<T> = Promise { resolve, _ -> resolve(value) }

    private fun ByteArray.asIntList(): List<Int> = map { it.toInt() and 0xFF }

    private fun ByteArray.toArrayBuffer(): ArrayBuffer =
        ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }
}
