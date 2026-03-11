package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("id", "text", "authorGid", "authorPublicKey", "authorSignature", "signatureVersion", "storedAt", "rootId")
data class FreezeSnapshotRecord(
    val id: String,
    val text: String,
    val authorGid: String,
    val authorPublicKey: String,
    val authorSignature: String,
    val signatureVersion: Int,
    val storedAt: Long,
    val rootId: String?,
)
