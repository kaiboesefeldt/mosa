package straightway.mosa.identity.privacy.snapshot

import kotlinx.coroutines.runBlocking
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.structuredstate.StructuredStateDatabase
import java.time.Clock
import kotlin.io.encoding.Base64

class FreezeSnapshotService(
    private val selectionService: FreezeSnapshotSelectionService,
    private val database: StructuredStateDatabase,
    private val postComplaintStoreFactory: PostComplaintStoreFactory,
    private val adminMessageStoreFactory: AdminMessageStoreFactory,
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

    private fun deleteRecords(records: List<FreezeSnapshotRecord>) {
        val contentIds = records.filterIsInstance<FreezeSnapshotContentRecord>().map(FreezeSnapshotContentRecord::id)
        val complaintIds = records.filterIsInstance<FreezeSnapshotComplaintRecord>().map(FreezeSnapshotComplaintRecord::id)
        val adminMessageIds =
            records
                .filterIsInstance<FreezeSnapshotAdminMessageRecord>()
                .map(FreezeSnapshotAdminMessageRecord::id)
        database.inTransaction { context ->
            val postComplaintStore = postComplaintStoreFactory.create(context)
            val adminMessageStore = adminMessageStoreFactory.create(context)
            postComplaintStore.deleteByIds(complaintIds)
            adminMessageStore.deleteByIds(adminMessageIds)
            runBlocking {
                vectorStore.writeAccess {
                    contentIds.forEach { remove(it) }
                }
            }
        }
    }
}
