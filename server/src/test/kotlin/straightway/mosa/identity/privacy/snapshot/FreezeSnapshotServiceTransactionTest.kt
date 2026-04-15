package straightway.mosa.identity.privacy.snapshot

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.content.complaint.PostComplaintsTable
import straightway.mosa.content.complaint.SqlitePostComplaintStore
import straightway.mosa.message.AdminMessageRecord
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.message.AdminMessagesTable
import straightway.mosa.message.SqliteAdminMessageStore
import straightway.mosa.structuredstate.StructuredStateDatabase
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FreezeSnapshotServiceTransactionTest {
    @Test
    fun `sqlite moderation deletions roll back when lucene deletion fails`() =
        runTest {
            val directory = Files.createTempDirectory("freeze-snapshot-service")
            val path = directory.resolve("mosa_db.sqlite")
            val database = StructuredStateDatabase(path)
            val complaintStore = SqlitePostComplaintStore(database)
            val adminMessageStore = SqliteAdminMessageStore(database)
            val selectionService = mockk<FreezeSnapshotSelectionService>()
            val archiveKey = FreezeSnapshotArchiveKey("archive-2026", ByteArray(16) { (it + 1).toByte() })
            val format = FreezeSnapshotFormat()

            try {
                database.initialize(PostComplaintsTable, AdminMessagesTable)
                complaintStore.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-owner",
                        postAuthorGid = "gid-other",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                adminMessageStore.append(
                    AdminMessageRecord(
                        id = "message-1",
                        recipientGid = "gid-owner",
                        senderGid = "gid-admin",
                        createdAt = 101,
                        subject = "Subject",
                        body = "Body",
                        expiresAt = 500,
                    ),
                )
                coEvery {
                    selectionService.resolveFor("gid-owner")
                } returns
                    listOf(
                        FreezeSnapshotContentRecord(
                            id = "root-1",
                            text = "Root",
                            authorGid = "gid-owner",
                            authorPublicKey = "public-key-root",
                            authorSignature = "signature-root",
                            signatureVersion = 7,
                            storedAt = 99,
                            rootId = null,
                        ),
                        FreezeSnapshotComplaintRecord(
                            id = "complaint-1",
                            ownerGid = "gid-owner",
                            postId = "post-1",
                            complainantGid = "gid-owner",
                            postAuthorGid = "gid-other",
                            reason = "Spam",
                            createdAt = 100,
                            complainantSignature = "sig-1",
                            complainantSignatureVersion = 1,
                            state = PostComplaintState.OPEN,
                        ),
                        FreezeSnapshotAdminMessageRecord(
                            id = "message-1",
                            ownerGid = "gid-owner",
                            recipientGid = "gid-owner",
                            senderGid = "gid-admin",
                            createdAt = 101,
                            subject = "Subject",
                            body = "Body",
                            expiresAt = 500,
                        ),
                    )

                val sut =
                    FreezeSnapshotService(
                        selectionService = selectionService,
                        database = database,
                        postComplaintStoreFactory = PostComplaintStoreFactory { context -> SqlitePostComplaintStore(context) },
                        adminMessageStoreFactory = AdminMessageStoreFactory { context -> SqliteAdminMessageStore(context) },
                        activeArchiveKeyProvider = FixedActiveArchiveKeyProvider(archiveKey),
                        nonceGenerator = FixedNonceGenerator(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                        format = format,
                        cryptoService = FreezeSnapshotCryptoService(TransactionTestArchiveKeyStore(archiveKey), format),
                        vectorStore = FailingDeleteVectorStore(),
                        clock = Clock.fixed(Instant.parse("2026-03-09T10:15:30Z"), ZoneOffset.UTC),
                        policy = FreezeSnapshotPolicy(podId = "pod-1"),
                    )

                assertFailsWith<IllegalStateException> {
                    sut.freeze("gid-owner")
                }

                assertEquals(listOf("complaint-1"), complaintStore.loadForIdentity("gid-owner").map(PostComplaintRecord::id))
                assertEquals(listOf("message-1"), adminMessageStore.loadForIdentity("gid-owner").map(AdminMessageRecord::id))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    private class FailingDeleteVectorStore : VectorStore {
        override suspend fun writeAccess(transactionBlock: suspend VectorStore.Transaction.() -> Unit) {
            transactionBlock(
                object : VectorStore.Transaction {
                    override suspend fun add(
                        id: String,
                        text: String,
                        fields: Map<String, String>,
                        vector: FloatArray,
                    ) = error("unexpected")

                    override suspend fun remove(id: String): Unit = throw IllegalStateException("lucene delete failed")
                },
            )
        }

        override suspend fun query(query: VectorStore.Query): VectorStore.QueryResults = VectorStore.QueryResults(emptyList(), null)

        override suspend fun queryOn(queryOnId: UUID?): VectorStore.QueryResults = VectorStore.QueryResults(emptyList(), null)

        override suspend fun fetch(ids: List<String>): List<VectorStore.PostDump> = emptyList()

        override suspend fun fetchByFieldValues(
            fieldName: String,
            values: Set<String>,
        ): List<VectorStore.PostDump> = emptyList()

        override fun dumpAll(): Flow<VectorStore.PostDump> = emptyFlow()

        override fun close() {
        }
    }

    private class FixedActiveArchiveKeyProvider(
        private val key: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotActiveArchiveKeyProvider {
        override fun activeKey(): FreezeSnapshotArchiveKey = key
    }

    private class FixedNonceGenerator(
        private val nonce: ByteArray,
    ) : FreezeSnapshotNonceGenerator {
        override fun generate(): ByteArray = nonce.copyOf()
    }

    private class TransactionTestArchiveKeyStore(
        private val key: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = key.takeIf { it.keyId == keyId }
    }
}
