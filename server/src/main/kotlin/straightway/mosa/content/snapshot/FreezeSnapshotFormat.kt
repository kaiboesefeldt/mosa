package straightway.mosa.content.snapshot

import java.nio.ByteBuffer
import kotlin.math.min

class FreezeSnapshotFormat(
    private val json: FreezeSnapshotJson = FreezeSnapshotJson(),
) {
    fun serialize(snapshot: FreezeSnapshot): ByteArray {
        val payloadBytes = serializePayload(snapshot.manifest, snapshot.records)
        return serializeFramed(snapshot.header, payloadBytes)
    }

    fun parse(bytes: ByteArray): FreezeSnapshot {
        val (header, payload) = parseFramed(bytes)
        val (manifest, records) = parsePayload(payload)
        return FreezeSnapshot(header, manifest, records)
    }

    fun serializeFramed(
        header: FreezeSnapshotHeader,
        payload: ByteArray,
    ): ByteArray {
        val headerBytes = serializeHeader(header).encodeToByteArray()
        return ByteBuffer
            .allocate(MAGIC_BYTES.size + Int.SIZE_BYTES + headerBytes.size + Int.SIZE_BYTES + payload.size)
            .put(MAGIC_BYTES)
            .putInt(headerBytes.size)
            .put(headerBytes)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    fun parseFramed(bytes: ByteArray): Pair<FreezeSnapshotHeader, ByteArray> {
        val buffer = ByteBuffer.wrap(bytes)
        requireMagic(buffer)
        val header = parseHeader(readFramePart(buffer, "header").decodeToString())
        val payload = readFramePart(buffer, "payload")
        ensureNoTrailingBytes(buffer)
        return header to payload
    }

    fun serializeHeader(header: FreezeSnapshotHeader): String {
        requireSupportedFormatVersion(header.formatVersion)
        return json.writeHeader(header)
    }

    fun parseHeader(serialized: String): FreezeSnapshotHeader =
        parseJson("header") { json.readHeader(serialized) }
            .also { requireSupportedFormatVersion(it.formatVersion) }

    fun serializeManifest(manifest: FreezeSnapshotManifest): String = json.writeManifest(manifest)

    fun parseManifest(serialized: String): FreezeSnapshotManifest = parseJson("manifest") { json.readManifest(serialized) }

    fun serializeRecords(records: List<FreezeSnapshotRecord>): ByteArray =
        records.joinToString("\n") { json.writeRecord(it) }.encodeToByteArray()

    fun parseRecords(serialized: ByteArray): List<FreezeSnapshotRecord> =
        serialized
            .decodeToString()
            .lineSequence()
            .filter { it.isNotEmpty() }
            .map { parseJson("record") { json.readRecord(it) } }
            .toList()

    fun serializePayload(
        manifest: FreezeSnapshotManifest,
        records: List<FreezeSnapshotRecord>,
    ): ByteArray {
        requireRecordCount(manifest, records)
        val manifestJson = serializeManifest(manifest)
        val recordsBytes = serializeRecords(records)
        return if (recordsBytes.isEmpty()) {
            "$manifestJson\n".encodeToByteArray()
        } else {
            "$manifestJson\n${recordsBytes.decodeToString()}\n".encodeToByteArray()
        }
    }

    fun parsePayload(serialized: ByteArray): Pair<FreezeSnapshotManifest, List<FreezeSnapshotRecord>> {
        val lines =
            serialized
                .decodeToString()
                .lineSequence()
                .filter { it.isNotEmpty() }
                .toList()
        if (lines.isEmpty()) {
            throw InvalidFreezeSnapshotException("Snapshot payload must contain a manifest")
        }
        val manifest = parseManifest(lines.first())
        val records = parseRecords(lines.drop(1).joinToString("\n").encodeToByteArray())
        requireRecordCount(manifest, records)
        return manifest to records
    }

    private fun <T> parseJson(
        label: String,
        parse: () -> T,
    ): T =
        try {
            parse()
        } catch (exception: Exception) {
            throw InvalidFreezeSnapshotException("Invalid snapshot $label", exception)
        }

    private fun requireSupportedFormatVersion(formatVersion: Int) {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw InvalidFreezeSnapshotException("Unsupported snapshot format version: $formatVersion")
        }
    }

    private fun requireRecordCount(
        manifest: FreezeSnapshotManifest,
        records: List<FreezeSnapshotRecord>,
    ) {
        if (manifest.recordCount != records.size) {
            throw InvalidFreezeSnapshotException("Snapshot manifest record count does not match payload")
        }
    }

    private fun requireMagic(buffer: ByteBuffer) {
        if (buffer.remaining() < MAGIC_BYTES.size) {
            throw InvalidFreezeSnapshotException("Snapshot framing is truncated")
        }
        val actual = ByteArray(MAGIC_BYTES.size)
        buffer.get(actual)
        if (!actual.contentEquals(MAGIC_BYTES)) {
            throw InvalidFreezeSnapshotException("Snapshot magic header is invalid")
        }
    }

    private fun readFramePart(
        buffer: ByteBuffer,
        label: String,
    ): ByteArray {
        val size = readSize(buffer, label)
        if (buffer.remaining() < size) {
            throw InvalidFreezeSnapshotException("Snapshot $label is truncated")
        }
        return ByteArray(size).also { buffer.get(it) }
    }

    private fun readSize(
        buffer: ByteBuffer,
        label: String,
    ): Int {
        if (buffer.remaining() < Int.SIZE_BYTES) {
            throw InvalidFreezeSnapshotException("Snapshot $label length is missing")
        }
        val size = buffer.int
        if (size < 0) {
            throw InvalidFreezeSnapshotException("Snapshot $label length is invalid")
        }
        return size
    }

    private fun ensureNoTrailingBytes(buffer: ByteBuffer) {
        if (buffer.hasRemaining()) {
            val trailingBytes = min(buffer.remaining(), 16)
            throw InvalidFreezeSnapshotException("Snapshot contains $trailingBytes unexpected trailing bytes")
        }
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val MAGIC = "MOSA"
        private val MAGIC_BYTES = MAGIC.encodeToByteArray()
    }
}
