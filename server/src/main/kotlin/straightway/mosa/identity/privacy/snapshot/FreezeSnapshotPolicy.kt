package straightway.mosa.identity.privacy.snapshot

data class FreezeSnapshotPolicy(
    val podId: String,
    val archivePolicyVersion: Int = 1,
)
