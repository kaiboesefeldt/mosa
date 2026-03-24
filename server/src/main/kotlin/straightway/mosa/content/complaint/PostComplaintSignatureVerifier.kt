package straightway.mosa.content.complaint

import straightway.mosa.Crypto
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.content.InvalidAuthenticatedPublicKeyException
import straightway.mosa.content.InvalidContentAuthorException
import java.security.PublicKey
import kotlin.io.encoding.Base64

class PostComplaintSignatureVerifier(
    private val crypto: Crypto,
    private val hasherFactory: HasherFactory,
    private val signatureVerifierProvider: SignatureVerifierProvider,
) {
    fun verifyCreate(
        principal: PodPrincipal,
        request: CreatePostComplaintRequest,
    ) {
        verify(
            principal = principal,
            signature = request.signature,
            signatureVersion = request.signatureVersion,
        ) {
            PostComplaintSignaturePayload
                .forComplaint(
                    postId = request.postId,
                    reason = request.reason,
                ).toCanonicalBytes()
        }
    }

    fun verifyDecision(
        principal: PodPrincipal,
        request: DecidePostComplaintRequest,
    ) {
        verify(
            principal = principal,
            signature = request.signature,
            signatureVersion = request.signatureVersion,
        ) {
            PostComplaintDecisionSignaturePayload
                .forDecision(
                    postId = request.postId,
                    targetStatus = request.targetStatus,
                    feedback = request.feedback,
                ).toCanonicalBytes()
        }
    }

    private fun verify(
        principal: PodPrincipal,
        signature: String,
        signatureVersion: Int,
        payloadProvider: () -> ByteArray,
    ) {
        requireSupportedVersion(signatureVersion)
        val publicKeyBytes = decodePublicKey(principal.publicKeyBase64)
        ensureMatchingGid(publicKeyBytes, principal.gid)
        val publicKey = deserializePublicKey(principal.publicKeyBase64)
        val signatureBytes = decodeSignature(signature)
        verifySignature(publicKey, signatureBytes, payloadProvider())
    }

    private fun requireSupportedVersion(signatureVersion: Int) {
        if (signatureVersion != PostComplaintSignatureVersion.V1) {
            throw UnsupportedPostComplaintSignatureVersionException()
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
            throw InvalidPostComplaintSignatureFormatException()
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
            throw InvalidPostComplaintSignatureException()
        }
    }
}
