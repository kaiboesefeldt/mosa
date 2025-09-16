package straightway.mosa.client.browser

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Uint8Array
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.js.Promise
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SubtleCryptoTest {
    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `sign delegates to wrapped subtle crypto`() = runTest {
        data class SignCall(val algo: String, val key: CryptoKey, val bytes: Uint8Array)

        val signingBytes = createUint8Array(9, 8, 7)
        val signResult = createUint8Array(1, 2, 3)
        val privateKey = createKey("signing-key")
        val signCalls = mutableListOf<SignCall>()
        val wrapped = js("({})")
        wrapped.sign = { algo: String, key: CryptoKey, bytes: Uint8Array ->
            signCalls += SignCall(algo, key, bytes)
            resolved(signResult.buffer)
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.sign("Ed25519", privateKey, signingBytes)

        val call = signCalls.single()
        assertEquals("Ed25519", call.algo)
        assertSame(privateKey, call.key)
        assertSame(signingBytes, call.bytes)
        assertContentEquals(signResult.asIntList(), Uint8Array(outcome).asIntList())
    }

    @Test
    fun `exportKey delegates to wrapped subtle crypto`() = runTest {
        data class ExportKeyCall(val format: String, val key: CryptoKey)

        val exportKey = createKey("export-key")
        val exportResult = createUint8Array(7, 8, 9, 10)
        val exportCalls = mutableListOf<ExportKeyCall>()
        val wrapped = js("({})")
        wrapped.exportKey = { format: String, key: CryptoKey ->
            exportCalls += ExportKeyCall(format, key)
            resolved(exportResult.buffer)
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.exportKey("raw", exportKey)

        val call = exportCalls.single()
        assertEquals("raw", call.format)
        assertSame(exportKey, call.key)
        assertContentEquals(exportResult.asIntList(), Uint8Array(outcome).asIntList())
    }

    @Test
    fun `generateKey delegates to wrapped subtle crypto`() = runTest {
        data class GenerateKeyCall(val algo: String, val exportable: Boolean, val usages: List<String>)

        val generatedPrivateKey = createKey("generated-private")
        val generatedPublicKey = createKey("generated-public")
        val keyPairResult = js("({})")
        keyPairResult.privateKey = generatedPrivateKey
        keyPairResult.publicKey = generatedPublicKey
        val generateCalls = mutableListOf<GenerateKeyCall>()
        val wrapped = js("({})")
        wrapped.generateKey = { algo: String, exportable: Boolean, usages: Array<String> ->
            generateCalls += GenerateKeyCall(algo, exportable, usages.toList())
            resolved(keyPairResult.unsafeCast<CryptoKeyPair>())
        }
        val sut = SubtleCrypto(wrapped)

        val generated = sut.generateKey("Ed25519", true, arrayOf("sign", "verify"))

        val call = generateCalls.single()
        assertEquals("Ed25519", call.algo)
        assertEquals(true, call.exportable)
        assertContentEquals(listOf("sign", "verify"), call.usages)
        assertSame(generatedPrivateKey, generated.privateKey)
        assertSame(generatedPublicKey, generated.publicKey)
    }

    @Test
    fun `digest delegates to wrapped subtle crypto`() = runTest {
        data class DigestCall(val algo: String, val data: Uint8Array)
        val digestBytes = createUint8Array(3, 4, 5, 6)
        val digestResult = createUint8Array(11, 12)
        val digestCalls = mutableListOf<DigestCall>()
        val wrapped = js("({})")
        wrapped.digest = { algo: String, data: Uint8Array ->
            digestCalls += DigestCall(algo, data)
            resolved(digestResult.buffer)
        }
        val sut = SubtleCrypto(wrapped)

        val outcome = sut.digest("SHA-512", digestBytes)

        val call = digestCalls.single()
        assertEquals("SHA-512", call.algo)
        assertSame(digestBytes, call.data)
        assertContentEquals(digestResult.asIntList(), Uint8Array(outcome).asIntList())
    }

    private fun createUint8Array(vararg ints: Int): Uint8Array =
        Uint8Array(ints.map { it.toByte() }.toTypedArray())

    private fun createKey(id: String): CryptoKey {
        val key = js("({})")
        key.id = id
        return key.unsafeCast<CryptoKey>()
    }

    private fun <T> resolved(value: T): Promise<T> = Promise { resolve, _ -> resolve(value) }

    private fun Uint8Array.asIntList(): List<Int> = (0 until length).map { asDynamic()[it] as Int }
}
