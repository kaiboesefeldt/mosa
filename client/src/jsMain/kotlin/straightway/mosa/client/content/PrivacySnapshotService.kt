package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.FreezeSnapshotRestoreResult

class PrivacySnapshotService(
    private val podSessionProvider: PodSessionProvider,
    private val transfer: PrivacySnapshotTransferPort,
) : PrivacySnapshotPort {
    override suspend fun freeze(fileBaseName: String) {
        val snapshot =
            podSessionProvider.execute("freeze privacy snapshot") { session ->
                session.podAccess.freezePrivacySnapshot(session.jwt)
            } ?: throw IllegalStateException("Privacy snapshot freeze failed")
        transfer.download(snapshot, fileBaseName)
    }

    override suspend fun restore(): FreezeSnapshotRestoreResult? {
        val snapshot = transfer.upload() ?: return null
        return podSessionProvider.execute("restore privacy snapshot") { session ->
            session.podAccess.restorePrivacySnapshot(snapshot, session.jwt)
        } ?: throw IllegalStateException("Privacy snapshot restore failed")
    }
}
