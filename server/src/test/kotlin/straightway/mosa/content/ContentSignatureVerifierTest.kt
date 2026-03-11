package straightway.mosa.content

import org.junit.jupiter.api.Test
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.crypto.toBase64
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import kotlin.io.encoding.Base64
import kotlin.test.assertFailsWith

class ContentSignatureVerifierTest {
    private val crypto = CryptoEd25519()
    private val verifier =
        ContentSignatureVerifier(
            crypto = crypto,
            hasherFactory = { MessageDigest.getInstance("SHA-256") },
            signatureVerifierProvider = { Signature.getInstance("Ed25519") },
        )
    private val keyPair = crypto.generateKeyPair()
    private val principal =
        PodPrincipal(
            certificate = PodCertificate(gid = deriveGid(keyPair), exp = 1L, sig = "sig"),
            publicKeyBase64 = keyPair.public.toBase64(),
        )

    @Test
    fun `accepts valid publish signature`() {
        verifier.verifyPublish(
            principal,
            PublishRequest(
                text = "Hello",
                signature = sign(keyPair, ContentSignaturePayload.forPost("Hello").toCanonicalBytes()),
            ),
        )
    }

    @Test
    fun `accepts valid reply signature`() {
        verifier.verifyReply(
            principal,
            ReplyRequest(
                content =
                    PublishRequest(
                        text = "Hello",
                        signature = sign(keyPair, ContentSignaturePayload.forReply("Hello", "root-1").toCanonicalBytes()),
                    ),
                rootId = "root-1",
            ),
        )
    }

    @Test
    fun `rejects unsupported signature version`() {
        assertFailsWith<UnsupportedContentSignatureVersionException> {
            verifier.verifyPublish(
                principal,
                PublishRequest(
                    text = "Hello",
                    signature = "sig",
                    signatureVersion = 2,
                ),
            )
        }
    }

    @Test
    fun `rejects malformed signature encoding`() {
        assertFailsWith<InvalidContentSignatureFormatException> {
            verifier.verifyPublish(
                principal,
                PublishRequest(
                    text = "Hello",
                    signature = "not base64",
                ),
            )
        }
    }

    @Test
    fun `rejects invalid cryptographic signature`() {
        assertFailsWith<InvalidContentSignatureException> {
            verifier.verifyPublish(
                principal,
                PublishRequest(
                    text = "Hello",
                    signature = sign(keyPair, ContentSignaturePayload.forPost("Other").toCanonicalBytes()),
                ),
            )
        }
    }

    @Test
    fun `rejects mismatching gid`() {
        assertFailsWith<InvalidContentAuthorException> {
            verifier.verifyPublish(
                principal.copy(certificate = principal.certificate.copy(gid = "other-gid")),
                PublishRequest(
                    text = "Hello",
                    signature = sign(keyPair, ContentSignaturePayload.forPost("Hello").toCanonicalBytes()),
                ),
            )
        }
    }

    @Test
    fun `rejects malformed authenticated public key`() {
        assertFailsWith<InvalidAuthenticatedPublicKeyException> {
            verifier.verifyPublish(
                principal.copy(publicKeyBase64 = "not base64"),
                PublishRequest(
                    text = "Hello",
                    signature = sign(keyPair, ContentSignaturePayload.forPost("Hello").toCanonicalBytes()),
                ),
            )
        }
    }

    @Test
    fun `rejects structurally invalid authenticated public key`() {
        val invalidPublicKeyBytes = byteArrayOf(1, 2, 3)
        assertFailsWith<InvalidAuthenticatedPublicKeyException> {
            verifier.verifyPublish(
                principal.copy(
                    certificate = principal.certificate.copy(gid = deriveGid(invalidPublicKeyBytes)),
                    publicKeyBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(invalidPublicKeyBytes),
                ),
                PublishRequest(
                    text = "Hello",
                    signature = sign(keyPair, ContentSignaturePayload.forPost("Hello").toCanonicalBytes()),
                ),
            )
        }
    }

    private fun deriveGid(keyPair: KeyPair): String =
        deriveGid(
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(keyPair.public.toBase64()),
        )

    private fun deriveGid(publicKeyBytes: ByteArray): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            MessageDigest.getInstance("SHA-256").digest(publicKeyBytes),
        )

    private fun sign(
        keyPair: KeyPair,
        payload: ByteArray,
    ): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            Signature.getInstance("Ed25519").run {
                initSign(keyPair.private)
                update(payload)
                sign()
            },
        )
}
