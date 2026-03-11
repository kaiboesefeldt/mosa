package straightway.mosa.content.snapshot

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FreezeSnapshotFormatTest {
    private val sut = FreezeSnapshotFormat()

    @Test
    fun `header serializes and parses losslessly`() {
        val header = sampleHeader()

        val serialized = sut.serializeHeader(header)

        assertEquals(header, sut.parseHeader(serialized))
    }

    @Test
    fun `manifest serializes and parses losslessly`() {
        val manifest = sampleManifest(recordCount = 2)

        val serialized = sut.serializeManifest(manifest)

        assertEquals(manifest, sut.parseManifest(serialized))
    }

    @Test
    fun `content records serialize and parse losslessly`() {
        val records = sampleRecords()

        val serialized = sut.serializeRecords(records)

        assertEquals(records, sut.parseRecords(serialized))
    }

    @Test
    fun `content records parser ignores empty lines`() {
        val serialized =
            """
            {"id":"root-1","text":"Root post","authorGid":"gid-owner","authorPublicKey":"public-key-root","authorSignature":"signature-root","signatureVersion":7,"storedAt":1700000001,"rootId":null}

            {"id":"reply-1","text":"Reply","authorGid":"gid-other","authorPublicKey":"public-key-reply","authorSignature":"signature-reply","signatureVersion":11,"storedAt":1700000002,"rootId":"root-1"}
            """.trimIndent().encodeToByteArray()

        assertEquals(sampleRecords(), sut.parseRecords(serialized))
    }

    @Test
    fun `full snapshot roundtrips deterministically`() {
        val snapshot =
            FreezeSnapshot(
                header = sampleHeader(),
                manifest = sampleManifest(recordCount = 2),
                records = sampleRecords(),
            )

        val serialized = sut.serialize(snapshot)
        val parsed = sut.parse(serialized)

        assertEquals(snapshot, parsed)
        assertContentEquals(serialized, sut.serialize(parsed))
    }

    @Test
    fun `unknown format version is rejected`() {
        val header = sampleHeader(formatVersion = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION + 1)

        assertFailsWith<InvalidFreezeSnapshotException> { sut.serializeHeader(header) }
    }

    @Test
    fun `parsing snapshot with unknown format version is rejected`() {
        val validHeader = sampleHeader()
        val invalidHeaderBytes =
            sut
                .serializeHeader(validHeader)
                .replace(
                    "\"formatVersion\":${validHeader.formatVersion}",
                    "\"formatVersion\":${validHeader.formatVersion + 1}",
                ).encodeToByteArray()
        val payloadBytes = sut.serializePayload(sampleManifest(recordCount = 0), emptyList())
        val bytes = frame(invalidHeaderBytes, payloadBytes)

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `malformed framing is rejected`() {
        val headerBytes = sut.serializeHeader(sampleHeader()).encodeToByteArray()
        val payloadBytes = sut.serializePayload(sampleManifest(recordCount = 0), emptyList())
        val bytes = frame(headerBytes, payloadBytes)
        bytes[0] = 'X'.code.toByte()

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `truncated magic header is rejected`() {
        val bytes = byteArrayOf('M'.code.toByte(), 'O'.code.toByte())

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `missing payload manifest is rejected`() {
        assertFailsWith<InvalidFreezeSnapshotException> { sut.parsePayload(byteArrayOf()) }
    }

    @Test
    fun `manifest record count mismatch is rejected during payload serialization`() {
        assertFailsWith<InvalidFreezeSnapshotException> {
            sut.serializePayload(sampleManifest(recordCount = 1), emptyList())
        }
    }

    @Test
    fun `manifest record count mismatch is rejected during payload parsing`() {
        val payload =
            """
            {"ownerGid":"gid-owner","recordCount":1,"archivePolicyVersion":1}
            """.trimIndent().encodeToByteArray()

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parsePayload(payload) }
    }

    @Test
    fun `truncated header frame is rejected`() {
        val headerBytes = sut.serializeHeader(sampleHeader()).encodeToByteArray()
        val payloadBytes = sut.serializePayload(sampleManifest(recordCount = 0), emptyList())
        val bytes =
            frame(headerBytes, payloadBytes)
                .copyOf(FreezeSnapshotFormat.MAGIC.length + Int.SIZE_BYTES + headerBytes.size - 1)

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `missing payload length is rejected`() {
        val headerBytes = sut.serializeHeader(sampleHeader()).encodeToByteArray()
        val bytes = frameWithoutPayloadLength(headerBytes)

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `negative header length is rejected`() {
        val bytes =
            ByteBuffer
                .allocate(FreezeSnapshotFormat.MAGIC.length + Int.SIZE_BYTES)
                .put(FreezeSnapshotFormat.MAGIC.encodeToByteArray())
                .putInt(-1)
                .array()

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `negative payload length is rejected`() {
        val headerBytes = sut.serializeHeader(sampleHeader()).encodeToByteArray()
        val bytes =
            ByteBuffer
                .allocate(FreezeSnapshotFormat.MAGIC.length + Int.SIZE_BYTES + headerBytes.size + Int.SIZE_BYTES)
                .put(FreezeSnapshotFormat.MAGIC.encodeToByteArray())
                .putInt(headerBytes.size)
                .put(headerBytes)
                .putInt(-1)
                .array()

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    @Test
    fun `unexpected trailing bytes are rejected`() {
        val headerBytes = sut.serializeHeader(sampleHeader()).encodeToByteArray()
        val payloadBytes = sut.serializePayload(sampleManifest(recordCount = 0), emptyList())
        val bytes = frame(headerBytes, payloadBytes) + byteArrayOf(1)

        assertFailsWith<InvalidFreezeSnapshotException> { sut.parse(bytes) }
    }

    private fun sampleHeader(formatVersion: Int = FreezeSnapshotFormat.CURRENT_FORMAT_VERSION) =
        FreezeSnapshotHeader(
            formatVersion = formatVersion,
            podId = "pod-1",
            keyId = "archive-2026",
            createdAt = Instant.ofEpochSecond(1_700_000_000),
            expiresAt = Instant.ofEpochSecond(1_731_536_000),
            nonce = "nonce-123",
        )

    private fun sampleManifest(recordCount: Int) =
        FreezeSnapshotManifest(
            ownerGid = "gid-owner",
            recordCount = recordCount,
            archivePolicyVersion = 1,
        )

    private fun sampleRecords() =
        listOf(
            FreezeSnapshotRecord(
                id = "root-1",
                text = "Root post",
                authorGid = "gid-owner",
                authorPublicKey = "public-key-root",
                authorSignature = "signature-root",
                signatureVersion = 7,
                storedAt = 1_700_000_001,
                rootId = null,
            ),
            FreezeSnapshotRecord(
                id = "reply-1",
                text = "Reply",
                authorGid = "gid-other",
                authorPublicKey = "public-key-reply",
                authorSignature = "signature-reply",
                signatureVersion = 11,
                storedAt = 1_700_000_002,
                rootId = "root-1",
            ),
        )

    private fun frame(
        headerBytes: ByteArray,
        payloadBytes: ByteArray,
    ): ByteArray {
        val magic = FreezeSnapshotFormat.MAGIC.encodeToByteArray()
        return ByteBuffer
            .allocate(magic.size + Int.SIZE_BYTES + headerBytes.size + Int.SIZE_BYTES + payloadBytes.size)
            .put(magic)
            .putInt(headerBytes.size)
            .put(headerBytes)
            .putInt(payloadBytes.size)
            .put(payloadBytes)
            .array()
    }

    private fun frameWithoutPayloadLength(headerBytes: ByteArray): ByteArray {
        val magic = FreezeSnapshotFormat.MAGIC.encodeToByteArray()
        return ByteBuffer
            .allocate(magic.size + Int.SIZE_BYTES + headerBytes.size)
            .put(magic)
            .putInt(headerBytes.size)
            .put(headerBytes)
            .array()
    }
}
