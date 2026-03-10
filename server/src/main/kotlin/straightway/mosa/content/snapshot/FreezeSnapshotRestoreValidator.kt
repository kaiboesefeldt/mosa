package straightway.mosa.content.snapshot

import straightway.mosa.content.MAX_POST_LENGTH
import java.time.Instant

class FreezeSnapshotRestoreValidator(
    private val sizePolicy: FreezeSnapshotSizePolicy,
) {
    fun validateHeader(
        header: FreezeSnapshotHeader,
        encryptedPayload: ByteArray,
        snapshotSize: Int,
        now: Instant,
        expectedPodId: String,
    ) {
        requirePodId(header, expectedPodId)
        requireNotExpired(header, now)
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

    private fun requireNotExpired(
        header: FreezeSnapshotHeader,
        now: Instant,
    ) {
        if (now > header.expiresAt) {
            throw InvalidFreezeSnapshotException("Snapshot has expired")
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
        if (records.map { it.id }.toSet().size != records.size) {
            throw InvalidFreezeSnapshotException("Snapshot contains duplicate record ids")
        }
    }

    private fun requireRecord(record: FreezeSnapshotRecord) {
        if (record.id.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record id must not be blank")
        }
        if (record.authorGid.isBlank()) {
            throw InvalidFreezeSnapshotException("Snapshot record authorGid must not be blank")
        }
        if (record.rootId?.isBlank() == true) {
            throw InvalidFreezeSnapshotException("Snapshot reply rootId must not be blank")
        }
        if (record.text.length > MAX_POST_LENGTH) {
            throw InvalidFreezeSnapshotException("Snapshot record text must be at most $MAX_POST_LENGTH characters")
        }
    }

    private fun requireReplyRoots(
        records: List<FreezeSnapshotRecord>,
        existingRootIds: Set<String>,
    ) {
        val rootIds = records.filter { it.rootId == null }.map { it.id }.toSet() + existingRootIds
        records
            .filter { it.rootId != null }
            .forEach { record ->
                if (record.rootId !in rootIds) {
                    throw InvalidFreezeSnapshotException("Snapshot reply references a missing root")
                }
            }
    }
}
