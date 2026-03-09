package straightway.mosa.client.content

import straightway.mosa.content.FreezeSnapshotRestoreResult

interface PrivacySnapshotPort {
    suspend fun freeze(fileBaseName: String)

    suspend fun restore(): FreezeSnapshotRestoreResult?
}
