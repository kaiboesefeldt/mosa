package straightway.mosa.client.content

import straightway.mosa.client.browser.KeyAlgorithm
import straightway.mosa.client.browser.KeyUsage
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignaturePayload
import straightway.mosa.content.ContentSignatureVersion
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryResult

class ContentSignatureVerifier(
    private val subtleCrypto: SubtleCrypto,
) {
    suspend fun verify(entry: FetchResult.Entry): ContentVerificationResult =
        verify(
            content = entry.content,
            authorGID = entry.authorGID,
            rootId = entry.rootId,
            authorPublicKey = entry.authorPublicKey,
            authorSignature = entry.authorSignature,
            signatureVersion = entry.signatureVersion,
        )

    suspend fun verify(entry: QueryResult.Entry): ContentVerificationResult =
        verify(
            content = entry.content,
            authorGID = entry.authorGID,
            rootId = entry.rootId,
            authorPublicKey = entry.authorPublicKey,
            authorSignature = entry.authorSignature,
            signatureVersion = entry.signatureVersion,
        )

    private suspend fun verify(
        content: String,
        authorGID: String,
        rootId: String?,
        authorPublicKey: String,
        authorSignature: String,
        signatureVersion: Int,
    ): ContentVerificationResult {
        if (signatureVersion != ContentSignatureVersion.V1) {
            return ContentVerificationResult.Invalid("unsupported signature version")
        }
        val publicKeyBytes = decode(authorPublicKey) ?: return invalid("invalid author public key")
        if (!hasMatchingGid(publicKeyBytes, authorGID)) {
            return invalid("author gid mismatch")
        }
        val signatureBytes = decode(authorSignature) ?: return invalid("invalid author signature")
        val publicKey = importPublicKey(publicKeyBytes) ?: return invalid("invalid author public key")
        val payload = payloadFor(content, rootId).toCanonicalBytes()
        val valid = runCatching { subtleCrypto.verify("Ed25519", publicKey, signatureBytes, payload) }.getOrDefault(false)
        return if (valid) ContentVerificationResult.Valid else invalid("invalid author signature")
    }

    private suspend fun hasMatchingGid(
        publicKeyBytes: ByteArray,
        authorGID: String,
    ): Boolean =
        runCatching {
            subtleCrypto.digest("SHA-256", publicKeyBytes).toBase64Url() == authorGID
        }.getOrDefault(false)

    private suspend fun importPublicKey(publicKeyBytes: ByteArray) =
        runCatching {
            subtleCrypto.importKey(
                format = "raw",
                keyData = publicKeyBytes,
                algorithm = KeyAlgorithm.Ed25519,
                exportable = true,
                usages = arrayOf(KeyUsage.Verify),
            )
        }.getOrNull()

    private fun payloadFor(
        content: String,
        rootId: String?,
    ): ContentSignaturePayload =
        if (rootId == null) {
            ContentSignaturePayload.forPost(content)
        } else {
            ContentSignaturePayload.forReply(content, rootId)
        }

    private fun decode(value: String): ByteArray? = runCatching { Base64.fromBase64Url(value) }.getOrNull()

    private fun invalid(reason: String) = ContentVerificationResult.Invalid(reason)
}

sealed interface ContentVerificationResult {
    data object Valid : ContentVerificationResult

    data class Invalid(
        val reason: String,
    ) : ContentVerificationResult
}
