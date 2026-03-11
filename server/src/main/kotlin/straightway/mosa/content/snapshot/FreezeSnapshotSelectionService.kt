package straightway.mosa.content.snapshot

import straightway.mosa.VectorStore
import straightway.mosa.content.ContentMetadataField
import straightway.mosa.content.requireContentMetadata
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

class FreezeSnapshotSelectionService(
    private val vectorStore: VectorStore,
) {
    suspend fun resolveFor(ownerGid: String): List<FreezeSnapshotRecord> {
        val ownRecords = vectorStore.fetchByFieldValues(AUTHOR_GID_KEY, setOf(ownerGid)).map { it.toFreezeSnapshotRecord() }
        val ownedRootIds = ownRecords.filter { it.isOwnedRoot() }.mapTo(linkedSetOf()) { it.id }
        val repliesOnOwnedRoots =
            vectorStore.fetchByFieldValues(ROOT_ID_KEY, ownedRootIds).map { it.toFreezeSnapshotRecord() }
        return (ownRecords + repliesOnOwnedRoots)
            .distinctBy { it.id }
            .sortedWith(compareBy<FreezeSnapshotRecord> { it.storedAt }.thenBy { it.id })
    }

    private fun FreezeSnapshotRecord.isOwnedRoot(): Boolean = rootId == null

    private fun VectorStore.PostDump.toFreezeSnapshotRecord() =
        FreezeSnapshotRecord(
            id = id,
            text = text,
            authorGid = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            authorPublicKey = fields.requireContentMetadata(id, ContentMetadataField.AuthorPublicKey),
            authorSignature = fields.requireContentMetadata(id, ContentMetadataField.AuthorSignature),
            signatureVersion = fields.requireContentMetadata(id, ContentMetadataField.SignatureVersion).toInt(),
            storedAt = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
            rootId = fields[ContentMetadataField.RootId.key],
        )
}
