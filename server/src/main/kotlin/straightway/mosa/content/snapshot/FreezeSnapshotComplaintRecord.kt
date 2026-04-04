package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonTypeName
import straightway.mosa.content.complaint.PostComplaintState

@JsonTypeName("complaint")
data class FreezeSnapshotComplaintRecord(
    override val id: String,
    val ownerGid: String,
    val postId: String,
    val complainantGid: String,
    val postAuthorGid: String,
    val reason: String,
    val createdAt: Long,
    val complainantSignature: String,
    val complainantSignatureVersion: Int,
    val state: PostComplaintState,
    val decidedByAdminGid: String? = null,
    val decidedAt: Long? = null,
    val feedback: String? = null,
    val adminSignature: String? = null,
    val adminSignatureVersion: Int? = null,
) : FreezeSnapshotRecord
