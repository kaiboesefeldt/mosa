package straightway.mosa.content.snapshot

data class FreezeSnapshotPolicy(
    val podId: String,
    val archivePolicyVersion: Int = 1,
)
