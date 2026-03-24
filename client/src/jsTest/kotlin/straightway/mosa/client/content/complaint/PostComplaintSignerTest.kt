package straightway.mosa.client.content.complaint

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
import straightway.mosa.content.complaint.PostComplaintDecisionSignaturePayload
import straightway.mosa.content.complaint.PostComplaintSignaturePayload
import straightway.mosa.content.complaint.PostComplaintState

class PostComplaintSignerTest {
    private val identity =
        Identity(
            gid = "gid-123",
            keyPair =
                js(
                    """({
                        privateKey: {},
                        publicKey: {}
                    })""",
                ).unsafeCast<CryptoKeyPair>(),
        )

    @Test
    fun `signs complaint payload canonically`() =
        runTest {
            val subtleCrypto = RecordingPostComplaintSubtleCrypto()
            val signer = PostComplaintSigner(SubtleCrypto(subtleCrypto.wrapped))

            val request = signer.signComplaint(identity, "post-1", "Spam")

            val payload = PostComplaintSignaturePayload.forComplaint("post-1", "Spam").toCanonicalBytes()
            assertEquals("post-1", request.postId)
            assertEquals("Spam", request.reason)
            assertEquals(payload.toBase64Url(), request.signature)
            assertEquals(payload.toList(), subtleCrypto.signCalls.single().bytes.toList())
        }

    @Test
    fun `signs complaint decision payload canonically`() =
        runTest {
            val subtleCrypto = RecordingPostComplaintSubtleCrypto()
            val signer = PostComplaintSigner(SubtleCrypto(subtleCrypto.wrapped))

            val request =
                signer.signDecision(
                    identity = identity,
                    complaintId = "complaint-1",
                    postId = "post-1",
                    targetStatus = PostComplaintState.REJECTED,
                    feedback = "Allowed",
                )

            val payload =
                PostComplaintDecisionSignaturePayload
                    .forDecision("post-1", PostComplaintState.REJECTED, "Allowed")
                    .toCanonicalBytes()
            assertEquals("complaint-1", request.complaintId)
            assertEquals(PostComplaintState.REJECTED, request.targetStatus)
            assertEquals(payload.toBase64Url(), request.signature)
            assertEquals(payload.toList(), subtleCrypto.signCalls.single().bytes.toList())
        }
}

private class RecordingPostComplaintSubtleCrypto {
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
