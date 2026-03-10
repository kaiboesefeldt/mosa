package straightway.mosa.content.snapshot

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
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
    private val policy = FreezeSnapshotPolicy(podId = "pod-1", snapshotLifetime = 365.days)

    @Test
    fun `valid snapshot restores all records`() =
        runTest {
            val snapshot = validSnapshot()
            val result = createSut().restore("gid-owner", snapshot)

            assertEquals(3, result.restoredCount)
            assertEquals(listOf("root-1", "reply-1", "reply-2"), result.restoredIds)
            assertEquals(
                listOf(
                    StoredEntry("root-1", "Root", "gid-owner", 100, null),
                    StoredEntry("reply-1", "Reply", "gid-owner", 101, "root-1"),
                    StoredEntry("reply-2", "Other reply", "gid-other", 102, "root-1"),
                ),
                vectorStore.entries(),
            )
            assertEquals(listOf("Root", "Reply", "Other reply"), embeddingService.embeddedTexts)
        }

    @Test
    fun `expired snapshot is rejected`() =
        runTest {
            val snapshot = snapshot(header = validHeader(expiresAt = clock.instant().minusSeconds(1)))

            assertRejected(snapshot, "Snapshot has expired")
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
                            FreezeSnapshotRecord(
                                id = "root-1",
                                text = "Root",
                                authorGid = "",
                                storedAt = 100,
                                rootId = null,
                            ),
                        ),
                )

            assertRejected(snapshot, "Snapshot record authorGid must not be blank")
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
            vectorStore.seed(StoredEntry("existing-root", "Existing root", "gid-existing", 7, null))
            val snapshot = snapshot(records = listOf(replyRecord(id = "reply-1", rootId = "existing-root")))

            val result = createSut().restore("gid-owner", snapshot)

            assertEquals(1, result.restoredCount)
            assertEquals(listOf("reply-1"), result.restoredIds)
            assertEquals(
                listOf(
                    StoredEntry("existing-root", "Existing root", "gid-existing", 7, null),
                    StoredEntry("reply-1", "Reply", "gid-owner", 101, "existing-root"),
                ),
                vectorStore.entries(),
            )
        }

    @Test
    fun `validation failure leaves pod data unchanged`() =
        runTest {
            val existing = StoredEntry("existing", "Existing", "gid-existing", 7, null)
            vectorStore.seed(existing)
            val snapshot = snapshot(records = listOf(replyRecord(rootId = "missing-root")))

            assertRejected(
                snapshot = snapshot,
                expectedMessage = "Snapshot reply references a missing root",
                expectedEntries = listOf(existing),
            )

            assertEquals(listOf(existing), vectorStore.entries())
        }

    private fun createSut(sizePolicy: FreezeSnapshotSizePolicy = FreezeSnapshotSizePolicy()) =
        FreezeSnapshotRestoreService(
            format = format,
            cryptoService = cryptoService,
            validator = FreezeSnapshotRestoreValidator(sizePolicy),
            embeddingService = embeddingService,
            vectorStore = vectorStore,
            clock = clock,
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

    private fun validHeader(
        podId: String = "pod-1",
        expiresAt: Instant = clock.instant().plusSeconds(365.days.inWholeSeconds),
    ) = FreezeSnapshotHeader(
        formatVersion = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION,
        podId = podId,
        keyId = archiveKey.keyId,
        createdAt = clock.instant(),
        expiresAt = expiresAt,
        nonce = "AQIDBAUGBwgJCgsM",
    )

    private fun validHeaderJson(): String = format.serializeHeader(validHeader())

    private fun snapshotWithRawHeader(rawHeader: String): ByteArray {
        val (_, payload) = format.parseFramed(validSnapshot())
        return frame(rawHeader.encodeToByteArray(), payload)
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
    ) = FreezeSnapshotRecord(
        id = id,
        text = text,
        authorGid = "gid-owner",
        storedAt = 100,
        rootId = null,
    )

    private fun replyRecord(
        id: String = "reply-1",
        rootId: String = "root-1",
    ) = FreezeSnapshotRecord(
        id = id,
        text = "Reply",
        authorGid = "gid-owner",
        storedAt = 101,
        rootId = rootId,
    )

    private fun otherReplyRecord() =
        FreezeSnapshotRecord(
            id = "reply-2",
            text = "Other reply",
            authorGid = "gid-other",
            storedAt = 102,
            rootId = "root-1",
        )

    private class FixedArchiveKeyStore(
        vararg keys: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        private val keysById = keys.associateBy { it.keyId }

        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = keysById[keyId]
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
}

private data class StoredEntry(
    val id: String,
    val text: String,
    val authorGid: String,
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
                    put(RECEIVED_AT_KEY, storedAt.toString())
                    rootId?.let { put(ROOT_ID_KEY, it) }
                },
        )
}
