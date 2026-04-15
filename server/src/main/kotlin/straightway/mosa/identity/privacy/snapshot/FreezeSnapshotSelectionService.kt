package straightway.mosa.identity.privacy.snapshot

import straightway.mosa.VectorStore
import straightway.mosa.content.ContentMetadataField
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.content.requireContentMetadata
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.structuredstate.StructuredStateContext
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

class FreezeSnapshotSelectionService(
    private val vectorStore: VectorStore,
    private val structuredStateContext: StructuredStateContext,
    private val postComplaintStoreFactory: PostComplaintStoreFactory,
    private val adminMessageStoreFactory: AdminMessageStoreFactory,
) {
    suspend fun resolveFor(ownerGid: String): List<FreezeSnapshotRecord> = resolveFor(ownerGid, structuredStateContext)

    suspend fun resolveFor(
        ownerGid: String,
        context: StructuredStateContext,
    ): List<FreezeSnapshotRecord> {
        val ownRecords = vectorStore.fetchByFieldValues(AUTHOR_GID_KEY, setOf(ownerGid)).map { it.toContentSnapshotRecord() }
        val ownedRootIds = ownRecords.filter { it.isOwnedRoot() }.mapTo(linkedSetOf()) { it.id }
        val repliesOnOwnedRoots =
            vectorStore.fetchByFieldValues(ROOT_ID_KEY, ownedRootIds).map { it.toContentSnapshotRecord() }
        val postComplaintStore = postComplaintStoreFactory.create(context)
        val adminMessageStore = adminMessageStoreFactory.create(context)
        val complaintRecords = postComplaintStore.loadForIdentity(ownerGid).map { it.toComplaintSnapshotRecord(ownerGid) }
        val adminMessageRecords = adminMessageStore.loadForIdentity(ownerGid).map { it.toAdminMessageSnapshotRecord(ownerGid) }
        return (ownRecords + repliesOnOwnedRoots + complaintRecords + adminMessageRecords)
            .distinctBy { it.snapshotKey() }
            .sortedWith(compareBy<FreezeSnapshotRecord> { it.sortTimestamp() }.thenBy { it.recordTypeOrder() }.thenBy { it.id })
    }

    private fun FreezeSnapshotContentRecord.isOwnedRoot(): Boolean = rootId == null

    private fun VectorStore.PostDump.toContentSnapshotRecord() =
        FreezeSnapshotContentRecord(
            id = id,
            text = text,
            authorGid = fields.requireContentMetadata(id, ContentMetadataField.AuthorGid),
            authorPublicKey = fields.requireContentMetadata(id, ContentMetadataField.AuthorPublicKey),
            authorSignature = fields.requireContentMetadata(id, ContentMetadataField.AuthorSignature),
            signatureVersion = fields.requireContentMetadata(id, ContentMetadataField.SignatureVersion).toInt(),
            storedAt = fields.requireContentMetadata(id, ContentMetadataField.ReceivedAt).toLong(),
            rootId = fields[ContentMetadataField.RootId.key],
        )

    private fun straightway.mosa.content.complaint.PostComplaintRecord.toComplaintSnapshotRecord(ownerGid: String) =
        FreezeSnapshotComplaintRecord(
            id = id,
            ownerGid = ownerGid,
            postId = postId,
            complainantGid = complainantGid,
            postAuthorGid = postAuthorGid,
            reason = reason,
            createdAt = createdAt,
            complainantSignature = complainantSignature,
            complainantSignatureVersion = complainantSignatureVersion,
            state = state,
            decidedByAdminGid = decidedByAdminGid,
            decidedAt = decidedAt,
            feedback = feedback,
            adminSignature = adminSignature,
            adminSignatureVersion = adminSignatureVersion,
        )

    private fun straightway.mosa.message.AdminMessageRecord.toAdminMessageSnapshotRecord(ownerGid: String) =
        FreezeSnapshotAdminMessageRecord(
            id = id,
            ownerGid = ownerGid,
            recipientGid = recipientGid,
            senderGid = senderGid,
            createdAt = createdAt,
            subject = subject,
            body = body,
            expiresAt = expiresAt,
        )

    private fun FreezeSnapshotRecord.snapshotKey(): String =
        when (this) {
            is FreezeSnapshotContentRecord -> "content:$id"
            is FreezeSnapshotComplaintRecord -> "complaint:$id"
            is FreezeSnapshotAdminMessageRecord -> "adminMessage:$id"
        }

    private fun FreezeSnapshotRecord.sortTimestamp(): Long =
        when (this) {
            is FreezeSnapshotContentRecord -> storedAt
            is FreezeSnapshotComplaintRecord -> createdAt
            is FreezeSnapshotAdminMessageRecord -> createdAt
        }

    private fun FreezeSnapshotRecord.recordTypeOrder(): Int =
        when (this) {
            is FreezeSnapshotContentRecord -> 0
            is FreezeSnapshotComplaintRecord -> 1
            is FreezeSnapshotAdminMessageRecord -> 2
        }
}
