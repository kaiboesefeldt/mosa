package straightway.mosa.identity.privacy.snapshot

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("content")
data class FreezeSnapshotContentRecord(
    override val id: String,
    val text: String,
    val authorGid: String,
    val authorPublicKey: String,
    val authorSignature: String,
    val signatureVersion: Int,
    val storedAt: Long,
    val rootId: String?,
) : FreezeSnapshotRecord
