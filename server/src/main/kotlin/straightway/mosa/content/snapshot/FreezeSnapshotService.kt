package straightway.mosa.content.snapshot

import straightway.mosa.VectorStore
import java.time.Clock
import kotlin.io.encoding.Base64

class FreezeSnapshotService(
    private val selectionService: FreezeSnapshotSelectionService,
    private val activeArchiveKeyProvider: FreezeSnapshotActiveArchiveKeyProvider,
    private val nonceGenerator: FreezeSnapshotNonceGenerator,
    private val format: FreezeSnapshotFormat,
    private val cryptoService: FreezeSnapshotCryptoService,
    private val vectorStore: VectorStore,
    private val clock: Clock,
    private val policy: FreezeSnapshotPolicy,
) {
    suspend fun freeze(ownerGid: String): ByteArray {
        val records = selectionService.resolveFor(ownerGid)
        val header = createHeader()
        val manifest = FreezeSnapshotManifest(ownerGid, records.size, policy.archivePolicyVersion)
        val payload = format.serializePayload(manifest, records)
        val encryptedPayload = cryptoService.encrypt(header, payload)
        val snapshot = format.serializeFramed(header, encryptedPayload)
        deleteRecords(records)
        return snapshot
    }

    private fun createHeader(): FreezeSnapshotHeader =
        FreezeSnapshotHeader(
            formatVersion = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION,
            podId = policy.podId,
            keyId = activeArchiveKeyProvider.activeKey().keyId,
            createdAt = clock.instant(),
            nonce = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(nonceGenerator.generate()),
        )

    private suspend fun deleteRecords(records: List<FreezeSnapshotRecord>) {
        vectorStore.writeAccess {
            records.forEach { remove(it.id) }
        }
    }
}
