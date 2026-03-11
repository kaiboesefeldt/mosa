package straightway.mosa.client.content

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignatureVersion

class ContentSignatureVerifierTest {
    @Test
    fun `accepts valid fetch entry`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)

        val result = sut.verify(subtle.validFetchEntry(id = "post-1", content = "Hello"))

        assertEquals(ContentVerificationResult.Valid, result)
    }

    @Test
    fun `rejects unsupported signature version`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val entry =
            subtle.validFetchEntry(id = "post-1", content = "Hello").copy(
                signatureVersion = ContentSignatureVersion.V1 + 1,
            )

        val result = sut.verify(entry)

        assertInvalidReason("unsupported signature version", result)
    }

    @Test
    fun `rejects invalid public key encoding`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val entry =
            subtle.validFetchEntry(id = "post-1", content = "Hello").copy(
                authorPublicKey = "%",
            )

        val result = sut.verify(entry)

        assertInvalidReason("invalid author public key", result)
    }

    @Test
    fun `rejects gid mismatch`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val entry =
            subtle.validFetchEntry(id = "post-1", content = "Hello").copy(
                authorGID = "other-gid",
            )

        val result = sut.verify(entry)

        assertInvalidReason("author gid mismatch", result)
    }

    @Test
    fun `rejects invalid signature encoding`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val entry =
            subtle.validFetchEntry(id = "post-1", content = "Hello").copy(
                authorSignature = "%",
            )

        val result = sut.verify(entry)

        assertInvalidReason("invalid author signature", result)
    }

    @Test
    fun `rejects invalid signature bytes`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val entry =
            subtle.validFetchEntry(id = "post-1", content = "Hello").copy(
                authorSignature = "wrong".encodeToByteArray().toBase64Url(),
            )

        val result = sut.verify(entry)

        assertInvalidReason("invalid author signature", result)
    }

    @Test
    fun `verifies replies with root id in payload`() = runTest {
        val subtle = FakeVerifierSubtleCrypto()
        val sut = ContentSignatureVerifier(subtle.subtleCrypto)
        val reply = subtle.validQueryEntry(id = "reply-1", content = "Hello", similarity = 0.4f, rootId = "root-1")

        val validResult = sut.verify(reply)
        val invalidResult = sut.verify(reply.copy(rootId = "other-root"))

        assertEquals(ContentVerificationResult.Valid, validResult)
        assertInvalidReason("invalid author signature", invalidResult)
    }

    private fun assertInvalidReason(
        expected: String,
        result: ContentVerificationResult,
    ) {
        val invalid = assertIs<ContentVerificationResult.Invalid>(result)
        assertEquals(expected, invalid.reason)
    }
}
