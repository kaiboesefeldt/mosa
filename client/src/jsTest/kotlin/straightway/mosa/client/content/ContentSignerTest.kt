package straightway.mosa.client.content

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignaturePayload
import straightway.mosa.content.ContentSignatureVersion
import straightway.mosa.content.PublishRequest

class ContentSignerTest {
    private val identity =
        Identity(
            gid = "gid-123",
            keyPair =
                js(
                    """({
                        privateKey: {},
                        publicKey: {}
                    })"""
                ).unsafeCast<CryptoKeyPair>(),
        )

    @Test
    fun `signPost signs canonical post payload`() = runTest {
        val subtle = RecordingSignerSubtleCrypto()
        val sut = ContentSigner(SubtleCrypto(subtle.wrapped))

        val result = sut.signPost(identity, "Hallo Welt")

        val canonicalPayload = ContentSignaturePayload.forPost("Hallo Welt").toCanonicalBytes()
        assertEquals(
            PublishRequest(
                text = "Hallo Welt",
                signature = canonicalPayload.toBase64Url(),
                signatureVersion = ContentSignatureVersion.V1,
            ),
            result,
        )
        val signCall = subtle.signCalls.single()
        assertEquals("Ed25519", signCall.algorithm)
        assertEquals(identity.keyPair.privateKey, signCall.privateKey)
        assertEquals(canonicalPayload.toList(), signCall.bytes.toList())
    }

    @Test
    fun `signReply signs canonical reply payload including root id`() = runTest {
        val subtle = RecordingSignerSubtleCrypto()
        val sut = ContentSigner(SubtleCrypto(subtle.wrapped))

        val result = sut.signReply(identity, "Antwort", "root-123")

        val canonicalPayload = ContentSignaturePayload.forReply("Antwort", "root-123").toCanonicalBytes()
        assertEquals(
            PublishRequest(
                text = "Antwort",
                signature = canonicalPayload.toBase64Url(),
                signatureVersion = ContentSignatureVersion.V1,
            ),
            result,
        )
        val signCall = subtle.signCalls.single()
        assertEquals("Ed25519", signCall.algorithm)
        assertEquals(identity.keyPair.privateKey, signCall.privateKey)
        assertEquals(canonicalPayload.toList(), signCall.bytes.toList())
    }
}

private class RecordingSignerSubtleCrypto {
    data class SignCall(
        val algorithm: String,
        val privateKey: CryptoKey,
        val bytes: ByteArray,
    )

    val signCalls = mutableListOf<SignCall>()
    val wrapped =
        run {
            val subtle = js("{}").unsafeCast<dynamic>()
            subtle.sign = { algorithm: String, privateKey: CryptoKey, bytes: ByteArray ->
                signCalls += SignCall(algorithm, privateKey, bytes)
                Promise.resolve(bytes.toArrayBuffer())
            }
            subtle
        }

    private fun ByteArray.toArrayBuffer(): ArrayBuffer =
        ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }
}
