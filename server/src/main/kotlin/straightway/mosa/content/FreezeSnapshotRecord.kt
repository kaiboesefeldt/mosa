package straightway.mosa.content

data class FreezeSnapshotRecord(
    val id: String,
    val text: String,
    val authorGid: String,
    val storedAt: Long,
    val rootId: String?,
)
