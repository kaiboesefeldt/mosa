package straightway.mosa.content.snapshot

import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY

class FreezeSnapshotRestoreService(
    private val format: FreezeSnapshotFormat,
    private val cryptoService: FreezeSnapshotCryptoService,
    private val validator: FreezeSnapshotRestoreValidator,
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
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
        val preparedRecords = prepareRecords(records)
        restoreRecords(preparedRecords)
        return FreezeSnapshotRestoreResult(records.map { it.id }, records.size)
    }

    private suspend fun resolveExistingRootIds(records: List<FreezeSnapshotRecord>): Set<String> {
        val snapshotRootIds = records.filter { it.rootId == null }.mapTo(linkedSetOf()) { it.id }
        val missingRootIds =
            records
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

    private suspend fun prepareRecords(records: List<FreezeSnapshotRecord>): List<PreparedRestoreRecord> =
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

    private suspend fun restoreRecords(records: List<PreparedRestoreRecord>) {
        vectorStore.writeAccess {
            records.forEach { record ->
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
