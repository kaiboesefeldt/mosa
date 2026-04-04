package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("adminMessage")
data class FreezeSnapshotAdminMessageRecord(
    override val id: String,
    val ownerGid: String,
    val recipientGid: String,
    val senderGid: String,
    val createdAt: Long,
    val subject: String,
    val body: String,
    val expiresAt: Long,
) : FreezeSnapshotRecord
