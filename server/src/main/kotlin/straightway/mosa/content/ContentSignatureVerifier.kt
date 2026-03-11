package straightway.mosa.content

import straightway.mosa.Crypto
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.auth.PodPrincipal
import java.security.PublicKey
import kotlin.io.encoding.Base64

class ContentSignatureVerifier(
    private val crypto: Crypto,
    private val hasherFactory: HasherFactory,
    private val signatureVerifierProvider: SignatureVerifierProvider,
) {
    fun verifyPublish(
        principal: PodPrincipal,
        request: PublishRequest,
    ) {
        verify(principal, request) {
            ContentSignaturePayload.forPost(request.text).toCanonicalBytes()
        }
    }

    fun verifyReply(
        principal: PodPrincipal,
        request: ReplyRequest,
    ) {
        verify(principal, request.content) {
            ContentSignaturePayload.forReply(request.content.text, request.rootId).toCanonicalBytes()
        }
    }

    private fun verify(
        principal: PodPrincipal,
        request: PublishRequest,
        payloadProvider: () -> ByteArray,
    ) {
        requireSupportedVersion(request.signatureVersion)
        val publicKeyBytes = decodePublicKey(principal.publicKeyBase64)
        ensureMatchingGid(publicKeyBytes, principal.gid)
        val publicKey = deserializePublicKey(principal.publicKeyBase64)
        val signatureBytes = decodeSignature(request.signature)
        verifySignature(publicKey, signatureBytes, payloadProvider())
    }

    private fun requireSupportedVersion(signatureVersion: Int) {
        if (signatureVersion != ContentSignatureVersion.V1) {
            throw UnsupportedContentSignatureVersionException()
        }
    }

    private fun decodePublicKey(publicKeyBase64: String): ByteArray =
        runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(publicKeyBase64)
        }.getOrElse {
            throw InvalidAuthenticatedPublicKeyException()
        }

    private fun ensureMatchingGid(
        publicKeyBytes: ByteArray,
        gid: String,
    ) {
        val derivedGid =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
                hasherFactory().digest(publicKeyBytes),
            )
        if (derivedGid != gid) {
            throw InvalidContentAuthorException()
        }
    }

    private fun deserializePublicKey(publicKeyBase64: String): PublicKey =
        runCatching {
            crypto.deserializePublicKey(publicKeyBase64)
        }.getOrElse {
            throw InvalidAuthenticatedPublicKeyException()
        }

    private fun decodeSignature(signatureBase64: String): ByteArray =
        runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(signatureBase64)
        }.getOrElse {
            throw InvalidContentSignatureFormatException()
        }

    private fun verifySignature(
        publicKey: PublicKey,
        signatureBytes: ByteArray,
        payload: ByteArray,
    ) {
        val verifier = signatureVerifierProvider.get()
        verifier.initVerify(publicKey)
        verifier.update(payload)
        if (!verifier.verify(signatureBytes)) {
            throw InvalidContentSignatureException()
        }
    }
}
