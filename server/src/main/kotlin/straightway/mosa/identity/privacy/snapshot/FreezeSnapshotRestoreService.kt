package straightway.mosa.identity.privacy.snapshot

import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.identity.privacy.FreezeSnapshotRestoreResult
import straightway.mosa.message.AdminMessageRecord
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.structuredstate.StructuredStateDatabase
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.time.Clock
import kotlin.time.Duration

class FreezeSnapshotRestoreService(
    private val format: FreezeSnapshotFormat,
    private val cryptoService: FreezeSnapshotCryptoService,
    private val validator: FreezeSnapshotRestoreValidator,
    private val embeddingService: EmbeddingService,
    private val database: StructuredStateDatabase,
    private val postComplaintStoreFactory: PostComplaintStoreFactory,
    private val adminMessageStoreFactory: AdminMessageStoreFactory,
    private val vectorStore: VectorStore,
    private val clock: Clock,
    private val adminMessageUnretrievedTtl: Duration,
    private val policy: FreezeSnapshotPolicy,
) {
    suspend fun restore(
        ownerGid: String,
        snapshotBytes: ByteArray,
    ): FreezeSnapshotRestoreResult {
        val (header, encryptedPayload) = format.parseFramed(snapshotBytes)
        validator.validateHeader(header, encryptedPayload, snapshotBytes.size, policy.podId)
        val payload = cryptoService.decrypt(header, encryptedPayload)
        validator.validatePayloadSize(payload.size)
        val (manifest, records) = format.parsePayload(payload)
        validator.validateRecords(ownerGid, manifest, records, resolveExistingRootIds(records))
        val preparedContentRecords = prepareContentRecords(records.filterIsInstance<FreezeSnapshotContentRecord>())
        restoreRecords(
            contentRecords = preparedContentRecords,
            complaintRecords = records.filterIsInstance<FreezeSnapshotComplaintRecord>(),
            adminMessageRecords = records.filterIsInstance<FreezeSnapshotAdminMessageRecord>(),
        )
        val restoredIds = records.map(FreezeSnapshotRecord::id)
        return FreezeSnapshotRestoreResult(restoredIds, restoredIds.size)
    }

    private suspend fun resolveExistingRootIds(records: List<FreezeSnapshotRecord>): Set<String> {
        val contentRecords = records.filterIsInstance<FreezeSnapshotContentRecord>()
        val snapshotRootIds = contentRecords.filter { it.rootId == null }.mapTo(linkedSetOf()) { it.id }
        val missingRootIds =
            contentRecords
                .mapNotNullTo(linkedSetOf()) { it.rootId }
                .filterNotTo(linkedSetOf()) { it in snapshotRootIds }
        if (missingRootIds.isEmpty()) {
            return emptySet()
        }
        return vectorStore
            .fetch(missingRootIds.toList())
            .asSequence()
            .filter { it.fields[ROOT_ID_KEY] == null }
            .mapTo(linkedSetOf()) { it.id }
    }

    private suspend fun prepareContentRecords(records: List<FreezeSnapshotContentRecord>): List<PreparedRestoreRecord> =
        records.map { record ->
            PreparedRestoreRecord(
                id = record.id,
                text = record.text,
                fields =
                    buildMap {
                        put(AUTHOR_GID_KEY, record.authorGid)
                        put(AUTHOR_PUBLIC_KEY_KEY, record.authorPublicKey)
                        put(AUTHOR_SIGNATURE_KEY, record.authorSignature)
                        put(SIGNATURE_VERSION_KEY, record.signatureVersion.toString())
                        put(RECEIVED_AT_KEY, record.storedAt.toString())
                        record.rootId?.let { put(ROOT_ID_KEY, it) }
                    },
                vector = embeddingService.embed(record.text),
            )
        }

    private fun restoreRecords(
        contentRecords: List<PreparedRestoreRecord>,
        complaintRecords: List<FreezeSnapshotComplaintRecord>,
        adminMessageRecords: List<FreezeSnapshotAdminMessageRecord>,
    ) {
        database.inTransaction { context ->
            val postComplaintStore = postComplaintStoreFactory.create(context)
            val adminMessageStore = adminMessageStoreFactory.create(context)
            complaintRecords.forEach { record ->
                postComplaintStore.restore(
                    PostComplaintRecord(
                        id = record.id,
                        postId = record.postId,
                        complainantGid = record.complainantGid,
                        postAuthorGid = record.postAuthorGid,
                        reason = record.reason,
                        createdAt = record.createdAt,
                        complainantSignature = record.complainantSignature,
                        complainantSignatureVersion = record.complainantSignatureVersion,
                        state = record.state,
                        decidedByAdminGid = record.decidedByAdminGid,
                        decidedAt = record.decidedAt,
                        feedback = record.feedback,
                        adminSignature = record.adminSignature,
                        adminSignatureVersion = record.adminSignatureVersion,
                    ),
                )
            }
            adminMessageRecords.forEach { record ->
                adminMessageStore.restore(
                    AdminMessageRecord(
                        id = record.id,
                        recipientGid = record.recipientGid,
                        senderGid = record.senderGid,
                        createdAt = record.createdAt,
                        subject = record.subject,
                        body = record.body,
                        expiresAt = clock.instant().epochSecond + adminMessageUnretrievedTtl.inWholeSeconds,
                        deliveryKey = restoredDeliveryKey(record.id),
                    ),
                )
            }
            kotlinx.coroutines.runBlocking {
                vectorStore.writeAccess {
                    contentRecords.forEach { remove(it.id) }
                    contentRecords.forEach { record ->
                        add(
                            id = record.id,
                            text = record.text,
                            fields = record.fields,
                            vector = record.vector,
                        )
                    }
                }
            }
        }
    }

    private fun restoredDeliveryKey(id: String): String = "$id:${clock.instant()}:restored"
}

internal data class PreparedRestoreRecord(
    val id: String,
    val text: String,
    val fields: Map<String, String>,
    val vector: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PreparedRestoreRecord

        if (id != other.id) return false
        if (text != other.text) return false
        if (fields != other.fields) return false
        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + fields.hashCode()
        result = 31 * result + vector.contentHashCode()
        return result
    }
}
