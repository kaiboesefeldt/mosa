package straightway.mosa.identity.privacy.snapshot

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.content.complaint.PostComplaintsTable
import straightway.mosa.content.complaint.SqlitePostComplaintStore
import straightway.mosa.message.AdminMessageRecord
import straightway.mosa.message.AdminMessageStore
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.message.AdminMessagesTable
import straightway.mosa.message.SqliteAdminMessageStore
import straightway.mosa.structuredstate.StructuredStateContext
import straightway.mosa.structuredstate.StructuredStateDatabase
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days

class FreezeSnapshotRestoreServiceTest {
    private val format = FreezeSnapshotFormat()
    private val archiveKey = FreezeSnapshotArchiveKey("archive-2026", ByteArray(16) { (it + 1).toByte() })
    private val cryptoService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(archiveKey), format)
    private val embeddingService = RecordingEmbeddingService()
    private val vectorStore = RecordingVectorStore()
    private val clock = Clock.fixed(Instant.parse("2026-03-09T10:15:30Z"), ZoneOffset.UTC)
    private val policy = FreezeSnapshotPolicy(podId = "pod-1")

    @Test
    fun `valid snapshot restores all records`() =
        runTest {
            val snapshot = validSnapshot()
            val result = createSut().restore("gid-owner", snapshot)

            assertEquals(3, result.restoredCount)
            assertEquals(listOf("root-1", "reply-1", "reply-2"), result.restoredIds)
            assertEquals(
                listOf(
                    StoredEntry("root-1", "Root", "gid-owner", "public-key-root", "signature-root", 7, 100, null),
                    StoredEntry("reply-1", "Reply", "gid-owner", "public-key-reply-1", "signature-reply-1", 11, 101, "root-1"),
                    StoredEntry("reply-2", "Other reply", "gid-other", "public-key-reply-2", "signature-reply-2", 13, 102, "root-1"),
                ),
                vectorStore.entries(),
            )
            assertEquals(listOf("Root", "Reply", "Other reply"), embeddingService.embeddedTexts)
        }

    @Test
    fun `restore replays moderation records and exposes them as unread`() =
        runTest {
            val directory = Files.createTempDirectory("freeze-snapshot-restore")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable, PostComplaintsTable)
                val complaintStore = SqlitePostComplaintStore(database)
                val adminMessageStore = SqliteAdminMessageStore(database)
                val snapshot =
                    snapshot(
                        manifest = FreezeSnapshotManifest("gid-owner", 2, 1),
                        records =
                            listOf(
                                complaintRecord(
                                    id = "complaint-1",
                                    state = straightway.mosa.content.complaint.PostComplaintState.CONFIRMED,
                                    decidedByAdminGid = "admin-1",
                                    decidedAt = 140,
                                    feedback = "Checked",
                                    adminSignature = "admin-signature",
                                    adminSignatureVersion = 3,
                                ),
                                adminMessageRecord(id = "message-1", expiresAt = 141),
                            ),
                    )

                val result =
                    createSut(
                        database = database,
                        postComplaintStoreFactory = PostComplaintStoreFactory { complaintStore },
                        adminMessageStoreFactory = AdminMessageStoreFactory { adminMessageStore },
                    ).restore("gid-owner", snapshot)

                assertEquals(2, result.restoredCount)
                assertEquals(listOf("complaint-1", "message-1"), result.restoredIds)
                assertEquals(1, complaintStore.countOpen())
                assertEquals(
                    listOf("complaint-1"),
                    complaintStore.loadPage(limit = 10, offset = 0, includeProcessed = false).map(PostComplaintRecord::id),
                )
                assertEquals(
                    listOf(
                        AdminMessageRecord(
                            id = "message-1",
                            recipientGid = "gid-owner",
                            senderGid = "admin-1",
                            createdAt = 130,
                            subject = "Subject",
                            body = "Body",
                            expiresAt = clock.instant().epochSecond + 28.days.inWholeSeconds,
                            deliveryKey = "message-1:${clock.instant()}:restored",
                        ),
                    ),
                    adminMessageStore.loadVisibleAndShortenExpiry(
                        recipientGid = "gid-owner",
                        nowEpochSeconds = clock.instant().epochSecond,
                        retrievedExpiryUpperBoundEpochSeconds = clock.instant().epochSecond + 28.days.inWholeSeconds,
                    ),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `snapshot for another pod is rejected`() =
        runTest {
            val snapshot = snapshot(header = validHeader(podId = "pod-2"))

            assertRejected(snapshot, "Snapshot pod binding does not match this pod")
        }

    @Test
    fun `unknown format version is rejected`() =
        runTest {
            val snapshot = snapshotWithRawHeader(validHeaderJson().replace("\"formatVersion\":1", "\"formatVersion\":2"))

            assertRejected(snapshot, "Unsupported snapshot format version: 2")
        }

    @Test
    fun `unknown keyId is rejected`() =
        runTest {
            val snapshot = snapshotWithRawHeader(validHeaderJson().replace("\"keyId\":\"archive-2026\"", "\"keyId\":\"archive-unknown\""))

            assertRejected(snapshot, "Unknown archive key: archive-unknown")
        }

    @Test
    fun `modified ciphertext is rejected`() =
        runTest {
            val snapshot = validSnapshot().copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }

            assertRejected(snapshot, "Snapshot payload decryption failed")
        }

    @Test
    fun `oversized snapshot is rejected`() =
        runTest {
            val snapshot = validSnapshot()
            val sizePolicy = FreezeSnapshotSizePolicy(maxSnapshotBytes = snapshot.size - 1, maxEncryptedPayloadBytes = snapshot.size)

            assertRejected(snapshot, "Snapshot size exceeds configured limit", sizePolicy)
        }

    @Test
    fun `manifest record count over configured limits is rejected`() =
        runTest {
            val snapshot = validSnapshot()
            val sizePolicy = FreezeSnapshotSizePolicy(maxSnapshotBytes = 65_536, maxEncryptedPayloadBytes = 65_536, maxRecordCount = 2)

            assertRejected(snapshot, "Snapshot record count exceeds configured limit", sizePolicy)
        }

    @Test
    fun `encrypted payload over configured limits is rejected`() =
        runTest {
            val snapshot = validSnapshot()
            val encryptedPayloadSize = format.parseFramed(snapshot).second.size
            val sizePolicy =
                FreezeSnapshotSizePolicy(
                    maxSnapshotBytes = 65_536,
                    maxEncryptedPayloadBytes = encryptedPayloadSize - 1,
                )

            assertRejected(snapshot, "Snapshot payload size exceeds configured limit", sizePolicy)
        }

    @Test
    fun `decrypted payload over configured limits is rejected`() =
        runTest {
            val records = listOf(rootRecord(), replyRecord(), otherReplyRecord())
            val payloadSize = format.serializePayload(FreezeSnapshotManifest("gid-owner", records.size, 1), records).size
            val sizePolicy =
                FreezeSnapshotSizePolicy(
                    maxSnapshotBytes = 65_536,
                    maxEncryptedPayloadBytes = 65_536,
                    maxDecryptedPayloadBytes = payloadSize - 1,
                )

            assertRejected(validSnapshot(), "Snapshot payload size exceeds configured limit", sizePolicy)
        }

    @Test
    fun `duplicate ids are rejected`() =
        runTest {
            val snapshot =
                snapshot(
                    records =
                        listOf(
                            rootRecord(id = "dup-1"),
                            rootRecord(id = "dup-1", text = "Duplicate"),
                        ),
                )

            assertRejected(snapshot, "Snapshot contains duplicate record ids")
        }

    @Test
    fun `snapshot owner mismatch is rejected`() =
        runTest {
            val snapshot = snapshot(manifest = FreezeSnapshotManifest("gid-other", 3, 1))

            assertRejected(snapshot, "Snapshot owner does not match authenticated user")
        }

    @Test
    fun `blank record id is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(rootRecord(id = "")))

            assertRejected(snapshot, "Snapshot record id must not be blank")
        }

    @Test
    fun `blank record author is rejected`() =
        runTest {
            val snapshot =
                snapshot(
                    records =
                        listOf(
                            FreezeSnapshotContentRecord(
                                id = "root-1",
                                text = "Root",
                                authorGid = "",
                                authorPublicKey = "public-key-root",
                                authorSignature = "signature-root",
                                signatureVersion = 7,
                                storedAt = 100,
                                rootId = null,
                            ),
                        ),
                )

            assertRejected(snapshot, "Snapshot record authorGid must not be blank")
        }

    @Test
    fun `blank record author public key is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(rootRecord().copy(authorPublicKey = "")))

            assertRejected(snapshot, "Snapshot record authorPublicKey must not be blank")
        }

    @Test
    fun `blank record author signature is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(rootRecord().copy(authorSignature = "")))

            assertRejected(snapshot, "Snapshot record authorSignature must not be blank")
        }

    @Test
    fun `non positive signature version is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(rootRecord().copy(signatureVersion = 0)))

            assertRejected(snapshot, "Snapshot record signatureVersion must be positive")
        }

    @Test
    fun `blank reply root id is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(replyRecord(rootId = "")))

            assertRejected(snapshot, "Snapshot reply rootId must not be blank")
        }

    @Test
    fun `too long record text is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(rootRecord(text = "x".repeat(1801))))

            assertRejected(snapshot, "Snapshot record text must be at most 1800 characters")
        }

    @Test
    fun `reply without existing root is rejected`() =
        runTest {
            val snapshot = snapshot(records = listOf(replyRecord(rootId = "missing-root")))

            assertRejected(snapshot, "Snapshot reply references a missing root")
        }

    @Test
    fun `reply on existing pod root is restored`() =
        runTest {
            vectorStore.seed(
                StoredEntry("existing-root", "Existing root", "gid-existing", "public-existing", "signature-existing", 17, 7, null),
            )
            val snapshot = snapshot(records = listOf(replyRecord(id = "reply-1", rootId = "existing-root")))

            val result = createSut().restore("gid-owner", snapshot)

            assertEquals(1, result.restoredCount)
            assertEquals(listOf("reply-1"), result.restoredIds)
            assertEquals(
                listOf(
                    StoredEntry("existing-root", "Existing root", "gid-existing", "public-existing", "signature-existing", 17, 7, null),
                    StoredEntry("reply-1", "Reply", "gid-owner", "public-key-reply-1", "signature-reply-1", 11, 101, "existing-root"),
                ),
                vectorStore.entries(),
            )
        }

    @Test
    fun `snapshot encrypted before key rotation is restored with historical archive key after rotation`() =
        runTest {
            val directory = Files.createTempDirectory("snapshot-key-store")
            val path = directory.resolve("mosa_db.sqlite")
            val freezeClock = Clock.fixed(Instant.parse("2026-03-09T10:15:30Z"), ZoneOffset.UTC)
            val restoreClock = Clock.fixed(Instant.parse("2027-04-02T08:00:00Z"), ZoneOffset.UTC)

            try {
                val database = snapshotArchiveKeyDatabase(path)
                val freezeKeyStore = SqliteFreezeSnapshotArchiveKeyStore(database, freezeClock)
                val freezeService =
                    FreezeSnapshotService(
                        selectionService =
                            mockk<FreezeSnapshotSelectionService>().also {
                                coEvery { it.resolveFor("gid-owner") } returns listOf(rootRecord(), replyRecord())
                            },
                        database = database,
                        postComplaintStoreFactory = PostComplaintStoreFactory { noOpPostComplaintStore() },
                        adminMessageStoreFactory = AdminMessageStoreFactory { noOpAdminMessageStore() },
                        activeArchiveKeyProvider = freezeKeyStore,
                        nonceGenerator = StaticNonceGenerator("AQIDBAUGBwgJCgsM"),
                        format = format,
                        cryptoService = FreezeSnapshotCryptoService(freezeKeyStore, format),
                        vectorStore = NoOpVectorStore(),
                        clock = freezeClock,
                        policy = policy,
                    )
                val snapshot = freezeService.freeze("gid-owner")
                val historicalKey = freezeKeyStore.activeKey()

                rotateArchiveKey(
                    database = database,
                    activeKeyId = "archive-2027",
                    activeSecret = ByteArray(16) { (it + 16).toByte() },
                    historicalKeyId = historicalKey.keyId,
                    createdAt = restoreClock.instant().epochSecond,
                )

                val rotatedKeyStore = SqliteFreezeSnapshotArchiveKeyStore(database, restoreClock)
                val restoreVectorStore = RecordingVectorStore()
                val restoreEmbeddingService = RecordingEmbeddingService()
                val restoreService =
                    FreezeSnapshotRestoreService(
                        format = format,
                        cryptoService = FreezeSnapshotCryptoService(rotatedKeyStore, format),
                        validator = FreezeSnapshotRestoreValidator(FreezeSnapshotSizePolicy()),
                        embeddingService = restoreEmbeddingService,
                        database = database,
                        postComplaintStoreFactory = PostComplaintStoreFactory { noOpPostComplaintStore() },
                        adminMessageStoreFactory = AdminMessageStoreFactory { noOpAdminMessageStore() },
                        vectorStore = restoreVectorStore,
                        clock = restoreClock,
                        adminMessageUnretrievedTtl = 180.days,
                        policy = policy,
                    )

                val result = restoreService.restore("gid-owner", snapshot)

                assertEquals("archive-2026", format.parseFramed(snapshot).first.keyId)
                assertEquals("archive-2027", rotatedKeyStore.activeKey().keyId)
                assertEquals(historicalKey, rotatedKeyStore.findByKeyId("archive-2026"))
                assertEquals(2, result.restoredCount)
                assertEquals(listOf("root-1", "reply-1"), result.restoredIds)
                assertEquals(
                    listOf(
                        StoredEntry("root-1", "Root", "gid-owner", "public-key-root", "signature-root", 7, 100, null),
                        StoredEntry("reply-1", "Reply", "gid-owner", "public-key-reply-1", "signature-reply-1", 11, 101, "root-1"),
                    ),
                    restoreVectorStore.entries(),
                )
                assertEquals(listOf("Root", "Reply"), restoreEmbeddingService.embeddedTexts)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `reply on existing pod reply is rejected`() =
        runTest {
            val existing =
                StoredEntry(
                    "existing-reply",
                    "Existing reply",
                    "gid-existing",
                    "public-existing",
                    "signature-existing",
                    17,
                    7,
                    "some-root",
                )
            vectorStore.seed(existing)
            val snapshot = snapshot(records = listOf(replyRecord(id = "reply-1", rootId = "existing-reply")))

            assertRejected(snapshot, "Snapshot reply references a missing root", expectedEntries = listOf(existing))
        }

    @Test
    fun `validation failure leaves pod data unchanged`() =
        runTest {
            val existing = StoredEntry("existing", "Existing", "gid-existing", "public-existing", "signature-existing", 17, 7, null)
            vectorStore.seed(existing)
            val snapshot = snapshot(records = listOf(replyRecord(rootId = "missing-root")))

            assertRejected(
                snapshot = snapshot,
                expectedMessage = "Snapshot reply references a missing root",
                expectedEntries = listOf(existing),
            )

            assertEquals(listOf(existing), vectorStore.entries())
        }

    private fun createSut(
        sizePolicy: FreezeSnapshotSizePolicy = FreezeSnapshotSizePolicy(),
        database: StructuredStateDatabase = noOpStructuredStateDatabase(),
        postComplaintStoreFactory: PostComplaintStoreFactory = PostComplaintStoreFactory { noOpPostComplaintStore() },
        adminMessageStoreFactory: AdminMessageStoreFactory = AdminMessageStoreFactory { noOpAdminMessageStore() },
    ) = FreezeSnapshotRestoreService(
        format = format,
        cryptoService = cryptoService,
        validator = FreezeSnapshotRestoreValidator(sizePolicy),
        embeddingService = embeddingService,
        database = database,
        postComplaintStoreFactory = postComplaintStoreFactory,
        adminMessageStoreFactory = adminMessageStoreFactory,
        vectorStore = vectorStore,
        clock = clock,
        adminMessageUnretrievedTtl = 180.days,
        policy = policy,
    )

    private suspend fun assertRejected(
        snapshot: ByteArray,
        expectedMessage: String,
        sizePolicy: FreezeSnapshotSizePolicy = FreezeSnapshotSizePolicy(),
        expectedEntries: List<StoredEntry> = emptyList(),
    ) {
        val exception =
            assertFailsWith<InvalidFreezeSnapshotException> {
                createSut(sizePolicy).restore("gid-owner", snapshot)
            }

        assertEquals(expectedMessage, exception.message)
        assertEquals(expectedEntries, vectorStore.entries())
    }

    private fun validSnapshot(): ByteArray = snapshot()

    private fun snapshot(
        header: FreezeSnapshotHeader = validHeader(),
        manifest: FreezeSnapshotManifest = FreezeSnapshotManifest("gid-owner", 3, 1),
        records: List<FreezeSnapshotRecord> = listOf(rootRecord(), replyRecord(), otherReplyRecord()),
    ): ByteArray {
        val payload = format.serializePayload(manifest.copy(recordCount = records.size), records)
        val encryptedPayload = cryptoService.encrypt(header, payload)
        return format.serializeFramed(header, encryptedPayload)
    }

    private fun validHeader(podId: String = "pod-1") =
        FreezeSnapshotHeader(
            formatVersion = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION,
            podId = podId,
            keyId = archiveKey.keyId,
            createdAt = clock.instant(),
            nonce = "AQIDBAUGBwgJCgsM",
        )

    private fun validHeaderJson(): String = format.serializeHeader(validHeader())

    private fun snapshotWithRawHeader(rawHeader: String): ByteArray {
        val (_, payload) = format.parseFramed(validSnapshot())
        return frame(rawHeader.encodeToByteArray(), payload)
    }

    private fun snapshotArchiveKeyDatabase(path: java.nio.file.Path): StructuredStateDatabase =
        StructuredStateDatabase(path).also { database ->
            database.initialize(SnapshotArchiveKeysTable)
            database.execute {
                SnapshotArchiveKeysTable.ensureIndexes()
            }
        }

    private fun rotateArchiveKey(
        database: StructuredStateDatabase,
        activeKeyId: String,
        activeSecret: ByteArray,
        historicalKeyId: String,
        createdAt: Long,
    ) {
        database.execute {
            SnapshotArchiveKeysTable.update(
                where = { SnapshotArchiveKeysTable.keyId eq historicalKeyId },
            ) { statement ->
                statement[SnapshotArchiveKeysTable.isActive] = false
            }
            SnapshotArchiveKeysTable.insert { statement ->
                statement[SnapshotArchiveKeysTable.keyId] = activeKeyId
                statement[SnapshotArchiveKeysTable.secret] =
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(activeSecret)
                statement[SnapshotArchiveKeysTable.isActive] = true
                statement[SnapshotArchiveKeysTable.createdAt] = createdAt
            }
        }
    }

    private fun frame(
        headerBytes: ByteArray,
        payloadBytes: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(FreezeSnapshotFormat.MAGIC.length + Int.SIZE_BYTES + headerBytes.size + Int.SIZE_BYTES + payloadBytes.size)
            .put(FreezeSnapshotFormat.MAGIC.encodeToByteArray())
            .putInt(headerBytes.size)
            .put(headerBytes)
            .putInt(payloadBytes.size)
            .put(payloadBytes)
            .array()

    private fun rootRecord(
        id: String = "root-1",
        text: String = "Root",
    ) = FreezeSnapshotContentRecord(
        id = id,
        text = text,
        authorGid = "gid-owner",
        authorPublicKey = "public-key-root",
        authorSignature = "signature-root",
        signatureVersion = 7,
        storedAt = 100,
        rootId = null,
    )

    private fun replyRecord(
        id: String = "reply-1",
        rootId: String = "root-1",
    ) = FreezeSnapshotContentRecord(
        id = id,
        text = "Reply",
        authorGid = "gid-owner",
        authorPublicKey = "public-key-reply-1",
        authorSignature = "signature-reply-1",
        signatureVersion = 11,
        storedAt = 101,
        rootId = rootId,
    )

    private fun otherReplyRecord() =
        FreezeSnapshotContentRecord(
            id = "reply-2",
            text = "Other reply",
            authorGid = "gid-other",
            authorPublicKey = "public-key-reply-2",
            authorSignature = "signature-reply-2",
            signatureVersion = 13,
            storedAt = 102,
            rootId = "root-1",
        )

    private fun complaintRecord(
        id: String = "complaint-1",
        state: straightway.mosa.content.complaint.PostComplaintState = straightway.mosa.content.complaint.PostComplaintState.OPEN,
        decidedByAdminGid: String? = null,
        decidedAt: Long? = null,
        feedback: String? = null,
        adminSignature: String? = null,
        adminSignatureVersion: Int? = null,
    ) = FreezeSnapshotComplaintRecord(
        id = id,
        ownerGid = "gid-owner",
        postId = "post-1",
        complainantGid = "gid-owner",
        postAuthorGid = "gid-other",
        reason = "Spam",
        createdAt = 120,
        complainantSignature = "complainant-signature",
        complainantSignatureVersion = 2,
        state = state,
        decidedByAdminGid = decidedByAdminGid,
        decidedAt = decidedAt,
        feedback = feedback,
        adminSignature = adminSignature,
        adminSignatureVersion = adminSignatureVersion,
    )

    private fun adminMessageRecord(
        id: String = "message-1",
        expiresAt: Long = 200,
    ) = FreezeSnapshotAdminMessageRecord(
        id = id,
        ownerGid = "gid-owner",
        recipientGid = "gid-owner",
        senderGid = "admin-1",
        createdAt = 130,
        subject = "Subject",
        body = "Body",
        expiresAt = expiresAt,
    )

    private fun noOpStructuredStateDatabase(): StructuredStateDatabase =
        mockk<StructuredStateDatabase>().also { database ->
            val context = mockk<StructuredStateContext>()
            every { database.inTransaction<Any>(any()) } answers {
                firstArg<(StructuredStateContext) -> Any>().invoke(context)
            }
        }

    private fun noOpPostComplaintStore(): PostComplaintStore =
        mockk<PostComplaintStore>().also { store ->
            every { store.deleteByIds(any()) } returns 0
            every { store.restore(any()) } returns Unit
            every { store.markAsRead(any()) } returns 0
        }

    private fun noOpAdminMessageStore(): AdminMessageStore =
        mockk<AdminMessageStore>().also { store ->
            every { store.deleteByIds(any()) } returns 0
            every { store.restore(any()) } returns Unit
        }

    private class FixedArchiveKeyStore(
        vararg keys: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        private val keysById = keys.associateBy { it.keyId }

        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = keysById[keyId]
    }

    private class StaticNonceGenerator(
        private val nonce: String,
    ) : FreezeSnapshotNonceGenerator {
        override fun generate(): ByteArray = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(nonce)
    }

    private class RecordingEmbeddingService : EmbeddingService {
        val embeddedTexts = mutableListOf<String>()

        override val dimension: Int = 2

        override suspend fun embed(text: String): FloatArray {
            embeddedTexts += text
            return floatArrayOf(text.length.toFloat(), text.sumOf { it.code }.toFloat())
        }
    }

    private class RecordingVectorStore : VectorStore {
        private val items = linkedMapOf<String, StoredEntry>()

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
                        staged[id] =
                            StoredEntry(
                                id = id,
                                text = text,
                                authorGid = fields.getValue(AUTHOR_GID_KEY),
                                authorPublicKey = fields.getValue(AUTHOR_PUBLIC_KEY_KEY),
                                authorSignature = fields.getValue(AUTHOR_SIGNATURE_KEY),
                                signatureVersion = fields.getValue(SIGNATURE_VERSION_KEY).toInt(),
                                storedAt = fields.getValue(RECEIVED_AT_KEY).toLong(),
                                rootId = fields[ROOT_ID_KEY],
                            )
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

        override suspend fun fetch(ids: List<String>): List<VectorStore.PostDump> = ids.mapNotNull(items::get).map { it.toPostDump() }

        override suspend fun fetchByFieldValues(
            fieldName: String,
            values: Set<String>,
        ): List<VectorStore.PostDump> = emptyList()

        override fun dumpAll(): Flow<VectorStore.PostDump> = emptyFlow()

        override fun close() {
        }

        fun seed(entry: StoredEntry) {
            items[entry.id] = entry
        }

        fun entries(): List<StoredEntry> = items.values.toList()
    }

    private class NoOpVectorStore : VectorStore {
        override suspend fun writeAccess(transactionBlock: suspend VectorStore.Transaction.() -> Unit) {
            transactionBlock(
                object : VectorStore.Transaction {
                    override suspend fun add(
                        id: String,
                        text: String,
                        fields: Map<String, String>,
                        vector: FloatArray,
                    ) = error("unexpected")

                    override suspend fun remove(id: String) {
                    }
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
}

private data class StoredEntry(
    val id: String,
    val text: String,
    val authorGid: String,
    val authorPublicKey: String,
    val authorSignature: String,
    val signatureVersion: Int,
    val storedAt: Long,
    val rootId: String?,
) {
    fun toPostDump() =
        VectorStore.PostDump(
            id = id,
            text = text,
            fields =
                buildMap {
                    put(AUTHOR_GID_KEY, authorGid)
                    put(AUTHOR_PUBLIC_KEY_KEY, authorPublicKey)
                    put(AUTHOR_SIGNATURE_KEY, authorSignature)
                    put(SIGNATURE_VERSION_KEY, signatureVersion.toString())
                    put(RECEIVED_AT_KEY, storedAt.toString())
                    rootId?.let { put(ROOT_ID_KEY, it) }
                },
        )
}
