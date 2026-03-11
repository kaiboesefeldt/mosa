package straightway.mosa.content.snapshot

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.VectorStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days

class FreezeSnapshotServiceTest {
    private val selectionService = mockk<FreezeSnapshotSelectionService>()
    private val activeArchiveKeyProvider = mockk<FreezeSnapshotActiveArchiveKeyProvider>()
    private val vectorStore = mockk<VectorStore>()
    private val format = FreezeSnapshotFormat()
    private val archiveKey = FreezeSnapshotArchiveKey("archive-2026", ByteArray(16) { (it + 1).toByte() })
    private val cryptoService = FreezeSnapshotCryptoService(FixedArchiveKeyStore(archiveKey), format)
    private val clock = Clock.fixed(Instant.parse("2026-03-09T10:15:30Z"), ZoneOffset.UTC)
    private val policy = FreezeSnapshotPolicy(podId = "pod-1", snapshotLifetime = 365.days)
    private val removedIds = mutableListOf<String>()

    @Test
    fun `successful freeze returns snapshot bytes`() =
        runTest {
            val records = listOf(rootRecord(), replyRecord())
            val sut = createSut(SequenceNonceGenerator(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
            givenSelection(records)
            givenActiveArchiveKey()
            givenDeletion()

            val snapshotBytes = sut.freeze("gid-owner")
            val (header, manifest, payloadRecords) = parseSnapshot(snapshotBytes)

            assertEquals("pod-1", header.podId)
            assertEquals("archive-2026", header.keyId)
            assertEquals(clock.instant(), header.createdAt)
            assertEquals(clock.instant().plusSeconds(365.days.inWholeSeconds), header.expiresAt)
            assertEquals("AQIDBAUGBwgJCgsM", header.nonce)
            assertEquals(FreezeSnapshotManifest("gid-owner", 2, 1), manifest)
            assertContentEquals(records.toTypedArray(), payloadRecords.toTypedArray())
            assertEquals("public-key-root", payloadRecords.first().authorPublicKey)
            assertEquals("signature-root", payloadRecords.first().authorSignature)
            assertEquals(7, payloadRecords.first().signatureVersion)
        }

    @Test
    fun `successful freeze deletes exactly the exported records`() =
        runTest {
            val records = listOf(rootRecord(), replyRecord(), otherReplyRecord())
            val sut = createSut(SequenceNonceGenerator(ByteArray(12) { it.toByte() }))
            givenSelection(records)
            givenActiveArchiveKey()
            givenDeletion()

            sut.freeze("gid-owner")

            assertEquals(listOf("root-1", "reply-1", "reply-2"), removedIds)
        }

    @Test
    fun `successful freeze uses a fresh nonce for each snapshot`() =
        runTest {
            val sut =
                createSut(
                    SequenceNonceGenerator(
                        ByteArray(12) { it.toByte() },
                        ByteArray(12) { (it + 12).toByte() },
                    ),
                )
            givenSelection(listOf(rootRecord()))
            givenActiveArchiveKey()
            givenDeletion()

            val first = format.parseFramed(sut.freeze("gid-owner")).first
            val second = format.parseFramed(sut.freeze("gid-owner")).first

            assertEquals("AAECAwQFBgcICQoL", first.nonce)
            assertEquals("DA0ODxAREhMUFRYX", second.nonce)
        }

    @Test
    fun `empty export set returns an empty valid snapshot without deletion`() =
        runTest {
            val sut = createSut(SequenceNonceGenerator(ByteArray(12) { it.toByte() }))
            givenSelection(emptyList())
            givenActiveArchiveKey()
            givenDeletion()

            val snapshotBytes = sut.freeze("gid-owner")
            val (_, manifest, records) = parseSnapshot(snapshotBytes)

            assertEquals(0, manifest.recordCount)
            assertEquals(emptyList(), records)
            assertEquals(emptyList(), removedIds)
        }

    @Test
    fun `serialization failure prevents deletion`() =
        runTest {
            val failingFormat = mockk<FreezeSnapshotFormat>()
            val sut = createSut(SequenceNonceGenerator(ByteArray(12) { it.toByte() }), format = failingFormat)
            givenSelection(listOf(rootRecord()))
            givenActiveArchiveKey()
            givenDeletion()
            every { failingFormat.serializePayload(any(), any()) } throws InvalidFreezeSnapshotException("boom")

            assertFailsWith<InvalidFreezeSnapshotException> {
                sut.freeze("gid-owner")
            }

            coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
        }

    @Test
    fun `encryption failure prevents deletion`() =
        runTest {
            val failingCrypto = mockk<FreezeSnapshotCryptoService>()
            val sut = createSut(SequenceNonceGenerator(ByteArray(12) { it.toByte() }), cryptoService = failingCrypto)
            givenSelection(listOf(rootRecord()))
            givenActiveArchiveKey()
            givenDeletion()
            every { failingCrypto.encrypt(any(), any()) } throws InvalidFreezeSnapshotException("boom")

            assertFailsWith<InvalidFreezeSnapshotException> {
                sut.freeze("gid-owner")
            }

            coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
        }

    @Test
    fun `nonce generation failure prevents deletion`() =
        runTest {
            val failingNonceGenerator = mockk<FreezeSnapshotNonceGenerator>()
            val sut = createSut(failingNonceGenerator)
            givenSelection(listOf(rootRecord()))
            givenActiveArchiveKey()
            givenDeletion()
            every { failingNonceGenerator.generate() } throws IllegalStateException("boom")

            assertFailsWith<IllegalStateException> {
                sut.freeze("gid-owner")
            }

            coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
        }

    @Test
    fun `active key resolution failure prevents deletion`() =
        runTest {
            val sut = createSut(SequenceNonceGenerator(ByteArray(12) { it.toByte() }))
            givenSelection(listOf(rootRecord()))
            givenDeletion()
            every { activeArchiveKeyProvider.activeKey() } throws IllegalStateException("boom")

            assertFailsWith<IllegalStateException> {
                sut.freeze("gid-owner")
            }

            coVerify(exactly = 0) { vectorStore.writeAccess(any()) }
        }

    private fun createSut(
        nonceGenerator: FreezeSnapshotNonceGenerator,
        format: FreezeSnapshotFormat = this.format,
        cryptoService: FreezeSnapshotCryptoService = this.cryptoService,
    ) = FreezeSnapshotService(
        selectionService = selectionService,
        activeArchiveKeyProvider = activeArchiveKeyProvider,
        nonceGenerator = nonceGenerator,
        format = format,
        cryptoService = cryptoService,
        vectorStore = vectorStore,
        clock = clock,
        policy = policy,
    )

    private fun givenSelection(records: List<FreezeSnapshotRecord>) {
        coEvery { selectionService.resolveFor("gid-owner") } returns records
    }

    private fun givenActiveArchiveKey() {
        every { activeArchiveKeyProvider.activeKey() } returns archiveKey
    }

    private fun givenDeletion() {
        removedIds.clear()
        coEvery { vectorStore.writeAccess(any()) } coAnswers {
            firstArg<suspend VectorStore.Transaction.() -> Unit>()(
                object : VectorStore.Transaction {
                    override suspend fun add(
                        id: String,
                        text: String,
                        fields: Map<String, String>,
                        vector: FloatArray,
                    ) = error("unexpected")

                    override suspend fun remove(id: String) {
                        removedIds += id
                    }
                },
            )
        }
    }

    private fun parseSnapshot(bytes: ByteArray): Triple<FreezeSnapshotHeader, FreezeSnapshotManifest, List<FreezeSnapshotRecord>> {
        val (header, encryptedPayload) = format.parseFramed(bytes)
        val payload = cryptoService.decrypt(header, encryptedPayload)
        val (manifest, records) = format.parsePayload(payload)
        return Triple(header, manifest, records)
    }

    private fun rootRecord() =
        FreezeSnapshotRecord(
            id = "root-1",
            text = "Root",
            authorGid = "gid-owner",
            authorPublicKey = "public-key-root",
            authorSignature = "signature-root",
            signatureVersion = 7,
            storedAt = 100,
            rootId = null,
        )

    private fun replyRecord() =
        FreezeSnapshotRecord(
            id = "reply-1",
            text = "Reply",
            authorGid = "gid-owner",
            authorPublicKey = "public-key-reply-1",
            authorSignature = "signature-reply-1",
            signatureVersion = 11,
            storedAt = 101,
            rootId = "root-1",
        )

    private fun otherReplyRecord() =
        FreezeSnapshotRecord(
            id = "reply-2",
            text = "Other",
            authorGid = "gid-other",
            authorPublicKey = "public-key-reply-2",
            authorSignature = "signature-reply-2",
            signatureVersion = 13,
            storedAt = 102,
            rootId = "root-1",
        )

    private class SequenceNonceGenerator(
        vararg nonces: ByteArray,
    ) : FreezeSnapshotNonceGenerator {
        private val values = ArrayDeque(nonces.map { it.copyOf() })

        override fun generate(): ByteArray = values.removeFirst()
    }

    private class FixedArchiveKeyStore(
        vararg keys: FreezeSnapshotArchiveKey,
    ) : FreezeSnapshotArchiveKeyStore {
        private val keysById = keys.associateBy { it.keyId }

        override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = keysById[keyId]
    }
}
