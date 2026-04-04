package straightway.mosa.content.snapshot

import straightway.mosa.content.MAX_POST_LENGTH

class FreezeSnapshotRestoreValidator(
    private val sizePolicy: FreezeSnapshotSizePolicy,
) {
    fun validateHeader(
        header: FreezeSnapshotHeader,
        encryptedPayload: ByteArray,
        snapshotSize: Int,
        expectedPodId: String,
    ) {
        requirePodId(header, expectedPodId)
        requireSnapshotSize(snapshotSize)
        requireEncryptedPayloadSize(encryptedPayload.size)
    }

    fun validatePayloadSize(payloadSize: Int) {
        if (payloadSize > sizePolicy.maxDecryptedPayloadBytes) {
            throw InvalidFreezeSnapshotException("Snapshot payload size exceeds configured limit")
        }
    }

    fun validateRecords(
        ownerGid: String,
        manifest: FreezeSnapshotManifest,
        records: List<FreezeSnapshotRecord>,
        existingRootIds: Set<String> = emptySet(),
    ) {
        requireOwner(ownerGid, manifest)
        requireRecordCount(manifest.recordCount)
        requireDistinctIds(records)
        records.forEach(::requireRecord)
        requireReplyRoots(records, existingRootIds)
    }

    private fun requirePodId(
        header: FreezeSnapshotHeader,
        expectedPodId: String,
    ) {
        if (header.podId != expectedPodId) {
            throw InvalidFreezeSnapshotException("Snapshot pod binding does not match this pod")
        }
    }

    private fun requireSnapshotSize(snapshotSize: Int) {
        if (snapshotSize > sizePolicy.maxSnapshotBytes) {
            throw InvalidFreezeSnapshotException("Snapshot size exceeds configured limit")
        }
    }

    private fun requireEncryptedPayloadSize(payloadSize: Int) {
        if (payloadSize > sizePolicy.maxEncryptedPayloadBytes) {
            throw InvalidFreezeSnapshotException("Snapshot payload size exceeds configured limit")
        }
    }

    private fun requireOwner(
        ownerGid: String,
        manifest: FreezeSnapshotManifest,
    ) {
        if (manifest.ownerGid != ownerGid) {
            throw InvalidFreezeSnapshotException("Snapshot owner does not match authenticated user")
        }
    }

    private fun requireRecordCount(recordCount: Int) {
        if (recordCount > sizePolicy.maxRecordCount) {
            throw InvalidFreezeSnapshotException("Snapshot record count exceeds configured limit")
        }
    }

    private fun requireDistinctIds(records: List<FreezeSnapshotRecord>) {
        if (records.map { it.snapshotKey() }.toSet().size != records.size) {
            throw InvalidFreezeSnapshotException("Snapshot contains duplicate record ids")
        }
    }

    private fun requireRecord(record: FreezeSnapshotRecord) {
        if (record.id.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record id must not be blank")
        }
        when (record) {
            is FreezeSnapshotContentRecord -> requireContentRecord(record)
            is FreezeSnapshotComplaintRecord -> requireComplaintRecord(record)
            is FreezeSnapshotAdminMessageRecord -> requireAdminMessageRecord(record)
        }
    }

    private fun requireReplyRoots(
        records: List<FreezeSnapshotRecord>,
        existingRootIds: Set<String>,
    ) {
        val contentRecords = records.filterIsInstance<FreezeSnapshotContentRecord>()
        val rootIds = contentRecords.filter { it.rootId == null }.map { it.id }.toSet() + existingRootIds
        records
            .filterIsInstance<FreezeSnapshotContentRecord>()
            .filter { it.rootId != null }
            .forEach { record ->
                if (record.rootId !in rootIds) {
                    throw InvalidFreezeSnapshotException("Snapshot reply references a missing root")
                }
            }
    }

    private fun requireContentRecord(record: FreezeSnapshotContentRecord) {
        if (record.authorGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record authorGid must not be blank")
        }
        if (record.authorPublicKey.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record authorPublicKey must not be blank")
        }
        if (record.authorSignature.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record authorSignature must not be blank")
        }
        if (record.signatureVersion <= 0) {
            throw InvalidFreezeSnapshotException("Snapshot record signatureVersion must be positive")
        }
        if (record.rootId?.isBlank() == true) {
            throw InvalidFreezeSnapshotException("Snapshot reply rootId must not be blank")
        }
        if (record.text.length > MAX_POST_LENGTH) {
            throw InvalidFreezeSnapshotException("Snapshot record text must be at most $MAX_POST_LENGTH characters")
        }
    }

    private fun requireComplaintRecord(record: FreezeSnapshotComplaintRecord) {
        if (record.ownerGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint ownerGid must not be blank")
        }
        if (record.postId.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint postId must not be blank")
        }
        if (record.complainantGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint complainantGid must not be blank")
        }
        if (record.postAuthorGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint postAuthorGid must not be blank")
        }
        if (record.reason.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint reason must not be blank")
        }
        if (record.complainantSignature.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot complaint complainantSignature must not be blank")
        }
        if (record.complainantSignatureVersion <= 0) {
            throw InvalidFreezeSnapshotException("Snapshot complaint complainantSignatureVersion must be positive")
        }
    }

    private fun requireAdminMessageRecord(record: FreezeSnapshotAdminMessageRecord) {
        if (record.ownerGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot admin message ownerGid must not be blank")
        }
        if (record.recipientGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot admin message recipientGid must not be blank")
        }
        if (record.senderGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot admin message senderGid must not be blank")
        }
        if (record.subject.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot admin message subject must not be blank")
        }
        if (record.body.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot admin message body must not be blank")
        }
    }

    private fun FreezeSnapshotRecord.snapshotKey(): String =
        when (this) {
            is FreezeSnapshotContentRecord -> "content:$id"
            is FreezeSnapshotComplaintRecord -> "complaint:$id"
            is FreezeSnapshotAdminMessageRecord -> "adminMessage:$id"
        }
}
