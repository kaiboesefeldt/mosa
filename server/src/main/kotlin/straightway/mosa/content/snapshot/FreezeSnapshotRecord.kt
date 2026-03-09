package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("id", "text", "authorGid", "storedAt", "rootId")
data class FreezeSnapshotRecord(
    val id: String,
    val text: String,
    val authorGid: String,
    val storedAt: Long,
    val rootId: String?,
)
