package straightway.mosa.message

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path

class FileBackedAdminMessageStore(
    private val path: Path,
) : AdminMessageStore {
    private val mutex = Mutex()
    private val objectMapper = jacksonObjectMapper().registerKotlinModule()

    override suspend fun append(record: AdminMessageRecord) {
        mutex.withLock {
            val records = readRecords().toMutableList()
            records += record
            writeRecords(records)
        }
    }

    override suspend fun deleteExpired(nowEpochSeconds: Long): Int =
        mutex.withLock {
            val records = readRecords()
            val retained = records.filter { nowEpochSeconds < it.expiresAt }
            writeRecords(retained)
            records.size - retained.size
        }

    override suspend fun loadVisibleForRecipient(
        recipientGid: String,
        nowEpochSeconds: Long,
    ): List<AdminMessageRecord> =
        mutex.withLock {
            val visible = mutableListOf<AdminMessageRecord>()
            val retained =
                readRecords().mapNotNull { record ->
                    if (record.expiresAt <= nowEpochSeconds) {
                        null
                    } else {
                        record.also {
                            if (record.recipientGid == recipientGid) {
                                visible += it
                            }
                        }
                    }
                }
            writeRecords(retained)
            visible
        }

    override suspend fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
    ): List<AdminMessageRecord> =
        mutex.withLock {
            val visible = mutableListOf<AdminMessageRecord>()
            val retained =
                readRecords().mapNotNull { record ->
                    if (record.expiresAt <= nowEpochSeconds) {
                        null
                    } else if (record.recipientGid == recipientGid) {
                        record
                            .copy(expiresAt = minOf(record.expiresAt, retrievedExpiryUpperBoundEpochSeconds))
                            .also { visible += it }
                    } else {
                        record
                    }
                }
            writeRecords(retained)
            visible
        }

    private fun readRecords(): List<AdminMessageRecord> {
        if (!Files.exists(path)) {
            return emptyList()
        }
        return objectMapper.readValue(path.toFile(), object : TypeReference<List<AdminMessageRecord>>() {})
    }

    private fun writeRecords(records: List<AdminMessageRecord>) {
        path.parent?.let { Files.createDirectories(it) }
        objectMapper.writeValue(path.toFile(), records)
    }
}
