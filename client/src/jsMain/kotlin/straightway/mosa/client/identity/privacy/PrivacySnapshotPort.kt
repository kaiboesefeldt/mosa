package straightway.mosa.client.identity.privacy

import straightway.mosa.identity.privacy.FreezeSnapshotRestoreResult

interface PrivacySnapshotPort {
    suspend fun freeze(fileBaseName: String)

    suspend fun restore(): FreezeSnapshotRestoreResult?
}
