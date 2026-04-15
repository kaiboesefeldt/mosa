package straightway.mosa.identity.privacy.snapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.EmbeddingService
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
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class FreezeSnapshotModerationIntegrationTest {
    @Test
    fun `freeze and restore keeps foreign moderation data out and restores owner data as unread`() =
        runTest {
            val directory = Files.createTempDirectory("freeze-snapshot-moderation")
            val path = directory.resolve("mosa_db.sqlite")
            val database = StructuredStateDatabase(path)
            val vectorStore = InMemoryVectorStore()
            val format = FreezeSnapshotFormat()
            val archiveKey = FreezeSnapshotArchiveKey("archive-2026", ByteArray(16) { (it + 1).toByte() })
            val clock = Clock.fixed(Instant.parse("2026-03-09T10:15:30Z"), ZoneOffset.UTC)
            val policy = FreezeSnapshotPolicy(podId = "pod-1")
            val complaintStoreFactory = PostComplaintStoreFactory { context -> SqlitePostComplaintStore(context) }
            val adminMessageStoreFactory = AdminMessageStoreFactory { context -> SqliteAdminMessageStore(context) }

            try {
                database.initialize(PostComplaintsTable, AdminMessagesTable)
                seedContent(vectorStore)
                seedComplaints(SqlitePostComplaintStore(database))
                seedAdminMessages(SqliteAdminMessageStore(database))

                val selectionService =
                    FreezeSnapshotSelectionService(
                        vectorStore = vectorStore,
                        structuredStateContext = database,
                        postComplaintStoreFactory = complaintStoreFactory,
                        adminMessageStoreFactory = adminMessageStoreFactory,
                    )
                val freezeService =
                    FreezeSnapshotService(
                        selectionService = selectionService,
                        database = database,
                        postComplaintStoreFactory = complaintStoreFactory,
                        adminMessageStoreFactory = adminMessageStoreFactory,
                        activeArchiveKeyProvider = FixedActiveArchiveKeyProvider(archiveKey),
                        nonceGenerator = FixedNonceGenerator(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)),
                        format = format,
                        cryptoService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(archiveKey), format),
                        vectorStore = vectorStore,
                        clock = clock,
                        policy = policy,
                    )

                val snapshot = freezeService.freeze("gid-owner")
                val payloadRecords = parseSnapshotRecords(snapshot, format, archiveKey)

                assertEquals(
                    listOf(
                        "root-owner",
                        "reply-owner-on-foreign-root",
                        "reply-foreign-on-owner-root",
                        "complaint-by-owner",
                        "complaint-about-owner",
                        "message-to-owner",
                        "message-from-owner",
                    ),
                    payloadRecords.map(FreezeSnapshotRecord::id),
                )
                assertEquals(
                    listOf("foreign-root", "reply-foreign-on-foreign-root"),
                    vectorStore.dumpIds(),
                )

                val complaintStoreAfterFreeze = SqlitePostComplaintStore(database)
                val adminMessageStoreAfterFreeze = SqliteAdminMessageStore(database)
                assertEquals(
                    listOf("complaint-foreign"),
                    complaintStoreAfterFreeze.loadForIdentity("gid-foreign").map(PostComplaintRecord::id),
                )
                assertEquals(
                    listOf("message-foreign"),
                    adminMessageStoreAfterFreeze.loadForIdentity("gid-foreign").map(AdminMessageRecord::id),
                )

                val restoreService =
                    FreezeSnapshotRestoreService(
                        format = format,
                        cryptoService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(archiveKey), format),
                        validator = FreezeSnapshotRestoreValidator(FreezeSnapshotSizePolicy()),
                        embeddingService = FixedEmbeddingService(),
                        database = database,
                        postComplaintStoreFactory = complaintStoreFactory,
                        adminMessageStoreFactory = adminMessageStoreFactory,
                        vectorStore = vectorStore,
                        clock = clock,
                        adminMessageUnretrievedTtl = 180.days,
                        policy = policy,
                    )

                val result = restoreService.restore("gid-owner", snapshot)

                assertEquals(7, result.restoredCount)
                assertEquals(
                    listOf(
                        "complaint-by-owner",
                        "complaint-about-owner",
                        "complaint-foreign",
                    ),
                    SqlitePostComplaintStore(
                        database,
                    ).loadPage(limit = 10, offset = 0, includeProcessed = true).map(PostComplaintRecord::id),
                )
                assertEquals(
                    listOf("complaint-by-owner", "complaint-about-owner", "complaint-foreign"),
                    SqlitePostComplaintStore(
                        database,
                    ).loadPage(limit = 10, offset = 0, includeProcessed = false).map(PostComplaintRecord::id),
                )
                assertEquals(
                    listOf("message-to-owner", "message-from-owner"),
                    SqliteAdminMessageStore(database).loadForIdentity("gid-owner").map(AdminMessageRecord::id),
                )
                assertEquals(
                    listOf("message-to-owner"),
                    SqliteAdminMessageStore(database)
                        .loadVisibleAndShortenExpiry(
                            recipientGid = "gid-owner",
                            nowEpochSeconds = clock.instant().epochSecond,
                            retrievedExpiryUpperBoundEpochSeconds = clock.instant().epochSecond + 28.days.inWholeSeconds,
                        ).map(AdminMessageRecord::id),
                )
                assertEquals(
                    listOf(
                        "foreign-root",
                        "reply-foreign-on-foreign-root",
                        "root-owner",
                        "reply-owner-on-foreign-root",
                        "reply-foreign-on-owner-root",
                    ),
                    vectorStore.dumpIds(),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    private fun seedContent(vectorStore: InMemoryVectorStore) {
        vectorStore.seed(content("root-owner", "Root owner", "gid-owner", 100, null))
        vectorStore.seed(content("reply-owner-on-foreign-root", "Reply owner", "gid-owner", 110, "foreign-root"))
        vectorStore.seed(content("reply-foreign-on-owner-root", "Reply foreign", "gid-foreign", 120, "root-owner"))
        vectorStore.seed(content("foreign-root", "Foreign root", "gid-foreign", 130, null))
        vectorStore.seed(content("reply-foreign-on-foreign-root", "Foreign reply", "gid-foreign", 140, "foreign-root"))
    }

    private fun seedComplaints(store: SqlitePostComplaintStore) {
        store.appendOpen(
            PostComplaintRecord(
                id = "complaint-by-owner",
                postId = "foreign-root",
                complainantGid = "gid-owner",
                postAuthorGid = "gid-foreign",
                reason = "Spam",
                createdAt = 150,
                complainantSignature = "sig-owner",
                complainantSignatureVersion = 1,
                state = PostComplaintState.OPEN,
            ),
        )
        store.restore(
            PostComplaintRecord(
                id = "complaint-about-owner",
                postId = "root-owner",
                complainantGid = "gid-foreign",
                postAuthorGid = "gid-owner",
                reason = "Abuse",
                createdAt = 160,
                complainantSignature = "sig-foreign",
                complainantSignatureVersion = 1,
                state = PostComplaintState.CONFIRMED,
                decidedByAdminGid = "gid-admin",
                decidedAt = 165,
                feedback = "Checked",
                adminSignature = "admin-sig",
                adminSignatureVersion = 2,
            ),
        )
        store.markAsRead(listOf("complaint-about-owner"))
        store.appendOpen(
            PostComplaintRecord(
                id = "complaint-foreign",
                postId = "foreign-root-2",
                complainantGid = "gid-foreign",
                postAuthorGid = "gid-other",
                reason = "Noise",
                createdAt = 170,
                complainantSignature = "sig-other",
                complainantSignatureVersion = 1,
                state = PostComplaintState.OPEN,
            ),
        )
    }

    private fun seedAdminMessages(store: SqliteAdminMessageStore) {
        store.append(
            AdminMessageRecord(
                id = "message-to-owner",
                recipientGid = "gid-owner",
                senderGid = "gid-admin",
                createdAt = 180,
                subject = "To owner",
                body = "Body 1",
                expiresAt = 10_000,
            ),
        )
        store.append(
            AdminMessageRecord(
                id = "message-from-owner",
                recipientGid = "gid-user",
                senderGid = "gid-owner",
                createdAt = 190,
                subject = "From owner",
                body = "Body 2",
                expiresAt = 10_000,
            ),
        )
        store.append(
            AdminMessageRecord(
                id = "message-foreign",
                recipientGid = "gid-foreign",
                senderGid = "gid-admin",
                createdAt = 200,
                subject = "Foreign",
                body = "Body 3",
                expiresAt = 10_000,
            ),
        )
    }

    private fun parseSnapshotRecords(
        snapshot: ByteArray,
        format: FreezeSnapshotFormat,
        archiveKey: FreezeSnapshotArchiveKey,
    ): List<FreezeSnapshotRecord> {
        val crypto = FreezeSnapshotCryptoService(FixedArchiveKeyStore(archiveKey), format)
        val (header, encryptedPayload) = format.parseFramed(snapshot)
        return format.parsePayload(crypto.decrypt(header, encryptedPayload)).second
    }

    private fun content(
        id: String,
        text: String,
        authorGid: String,
        storedAt: Long,
        rootId: String?,
    ) = StoredVectorEntry(
        id = id,
        text = text,
        fields =
            buildMap {
                put(AUTHOR_GID_KEY, authorGid)
                put(AUTHOR_PUBLIC_KEY_KEY, "public-$id")
                put(AUTHOR_SIGNATURE_KEY, "signature-$id")
                put(SIGNATURE_VERSION_KEY, "1")
                put(RECEIVED_AT_KEY, storedAt.toString())
                rootId?.let { put(ROOT_ID_KEY, it) }
            },
    )

    private class FixedActiveArchiveKeyProvider(
        private val key: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotActiveArchiveKeyProvider {
        override fun activeKey(): FreezeSnapshotArchiveKey = key
    }

    private class FixedArchiveKeyStore(
        private val key: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = key.takeIf { it.keyId == keyId }
    }

    private class FixedNonceGenerator(
        private val nonce: ByteArray,
    ) : FreezeSnapshotNonceGenerator {
        override fun generate(): ByteArray = nonce.copyOf()
    }

    private class FixedEmbeddingService : EmbeddingService {
        override val dimension: Int = 2

        override suspend fun embed(text: String): FloatArray = floatArrayOf(text.length.toFloat(), text.length.toFloat())
    }

    private class InMemoryVectorStore : VectorStore {
        private val items = linkedMapOf<String, StoredVectorEntry>()

        override suspend fun writeAccess(transactionBlock: suspend VectorStore.Transaction.() -> Unit) {
            val staged = LinkedHashMap(items)
            transactionBlock(
                object : VectorStore.Transaction {
                    override suspend fun add(
                        id: String,
                        text: String,
                        fields: Map<String, String>,
                        vector: FloatArray,
                    ) {
                        staged[id] = StoredVectorEntry(id, text, fields)
                    }

                    override suspend fun remove(id: String) {
                        staged.remove(id)
                    }
                },
            )
            items.clear()
            items.putAll(staged)
        }

        override suspend fun query(query: VectorStore.Query): VectorStore.QueryResults = VectorStore.QueryResults(emptyList(), null)

        override suspend fun queryOn(queryOnId: UUID?): VectorStore.QueryResults = VectorStore.QueryResults(emptyList(), null)

        override suspend fun fetch(ids: List<String>): List<VectorStore.PostDump> =
            ids.mapNotNull(items::get).map(StoredVectorEntry::toDump)

        override suspend fun fetchByFieldValues(
            fieldName: String,
            values: Set<String>,
        ): List<VectorStore.PostDump> =
            items.values
                .filter { entry -> entry.fields[fieldName] in values }
                .map(StoredVectorEntry::toDump)

        override fun dumpAll(): Flow<VectorStore.PostDump> = emptyFlow()

        override fun close() {
        }

        fun seed(entry: StoredVectorEntry) {
            items[entry.id] = entry
        }

        fun dumpIds(): List<String> = items.keys.toList()
    }

    private data class StoredVectorEntry(
        val id: String,
        val text: String,
        val fields: Map<String, String>,
    ) {
        fun toDump() = VectorStore.PostDump(id = id, text = text, fields = fields)
    }
}
