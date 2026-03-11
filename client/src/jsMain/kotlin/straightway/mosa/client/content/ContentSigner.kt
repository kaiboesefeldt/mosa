package straightway.mosa.client.content

import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignaturePayload
import straightway.mosa.content.PublishRequest

class ContentSigner(
    private val subtleCrypto: SubtleCrypto,
) {
    suspend fun signPost(
        identity: Identity,
        text: String,
    ): PublishRequest = signedRequest(identity, ContentSignaturePayload.forPost(text))

    suspend fun signReply(
        identity: Identity,
        text: String,
        rootId: String,
    ): PublishRequest = signedRequest(identity, ContentSignaturePayload.forReply(text, rootId))

    private suspend fun signedRequest(
        identity: Identity,
        payload: ContentSignaturePayload,
    ): PublishRequest =
        PublishRequest(
            text = payload.text,
            signature = sign(identity, payload),
            signatureVersion = payload.version,
        )

    private suspend fun sign(
        identity: Identity,
        payload: ContentSignaturePayload,
    ): String =
        subtleCrypto
            .sign("Ed25519", identity.keyPair.privateKey, payload.toCanonicalBytes())
            .toBase64Url()
}
