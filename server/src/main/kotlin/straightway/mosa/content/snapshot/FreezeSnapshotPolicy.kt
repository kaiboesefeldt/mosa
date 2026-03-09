package straightway.mosa.content.snapshot

import kotlin.time.Duration

data class FreezeSnapshotPolicy(
    val podId: String,
    val snapshotLifetime: Duration,
    val archivePolicyVersion: Int = 1,
)
