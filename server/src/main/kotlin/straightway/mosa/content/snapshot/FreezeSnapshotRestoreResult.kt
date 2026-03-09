package straightway.mosa.content.snapshot

data class FreezeSnapshotRestoreResult(
    val restoredIds: List<String>,
    val restoredCount: Int,
)
