package straightway.mosa.identity.privacy

import kotlinx.serialization.Serializable

@Serializable
data class FreezeSnapshotRestoreResult(
    val restoredIds: List<String>,
    val restoredCount: Int,
)
