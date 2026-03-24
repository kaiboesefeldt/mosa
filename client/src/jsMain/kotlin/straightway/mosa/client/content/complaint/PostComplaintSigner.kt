package straightway.mosa.client.content.complaint

import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.complaint.CreatePostComplaintRequest
import straightway.mosa.content.complaint.DecidePostComplaintRequest
import straightway.mosa.content.complaint.PostComplaintDecisionSignaturePayload
import straightway.mosa.content.complaint.PostComplaintSignaturePayload
import straightway.mosa.content.complaint.PostComplaintState

open class PostComplaintSigner(
    private val subtleCrypto: SubtleCrypto,
) {
    open suspend fun signComplaint(
        identity: Identity,
        postId: String,
        reason: String,
    ): CreatePostComplaintRequest {
        val payload = PostComplaintSignaturePayload.forComplaint(postId = postId, reason = reason)
        return CreatePostComplaintRequest(
            postId = postId,
            reason = reason,
            signature = sign(identity, payload.toCanonicalBytes()),
            signatureVersion = payload.version,
        )
    }

    open suspend fun signDecision(
        identity: Identity,
        complaintId: String,
        postId: String,
        targetStatus: PostComplaintState,
        feedback: String,
    ): DecidePostComplaintRequest {
        val payload =
            PostComplaintDecisionSignaturePayload.forDecision(
                postId = postId,
                targetStatus = targetStatus,
                feedback = feedback,
            )
        return DecidePostComplaintRequest(
            complaintId = complaintId,
            postId = postId,
            targetStatus = targetStatus,
            feedback = feedback,
            signature = sign(identity, payload.toCanonicalBytes()),
            signatureVersion = payload.version,
        )
    }

    private suspend fun sign(
        identity: Identity,
        payload: ByteArray,
    ): String =
        subtleCrypto
            .sign("Ed25519", identity.keyPair.privateKey, payload)
            .toBase64Url()
}
