package straightway.mosa.content.snapshot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import kotlin.io.encoding.Base64

class FileBackedFreezeSnapshotArchiveKeyStore(
    private val path: Path,
    private val clock: Clock,
) : FreezeSnapshotArchiveKeyStore,
    FreezeSnapshotActiveArchiveKeyProvider {
    private val state: ArchiveKeyState by lazy { loadOrCreateState() }
    private val mapper = ObjectMapper().registerKotlinModule()

    override fun activeKey(): FreezeSnapshotArchiveKey = state.activeKey

    override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = state.keysById[keyId]

    private fun loadOrCreateState(): ArchiveKeyState =
        if (Files.exists(path)) {
            readState()
        } else {
            createState()
        }

    private fun readState(): ArchiveKeyState {
        val persisted =
            try {
                mapper.readValue<PersistedArchiveKeyFile>(Files.readString(path))
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid snapshot archive key file")
            }
        val keysById =
            persisted.keys
                .onEach { require(it.keyId.isNotBlank()) { "Invalid snapshot archive key file" } }
                .associate { it.keyId to FreezeSnapshotArchiveKey(it.keyId, decodeSecret(it.secret)) }
        require(keysById.size == persisted.keys.size) { "Invalid snapshot archive key file" }
        val activeKey = keysById[persisted.activeKeyId] ?: throw IllegalArgumentException("Invalid snapshot archive key file")
        return ArchiveKeyState(activeKey, keysById)
    }

    private fun createState(): ArchiveKeyState {
        Files.createDirectories(path.parent ?: Path.of("."))
        val activeKey =
            FreezeSnapshotArchiveKey(
                keyId = "archive-${clock.instant().atZone(ZoneOffset.UTC).year}",
                secret = randomSecret(),
            )
        Files.writeString(
            path,
            mapper.writeValueAsString(
                PersistedArchiveKeyFile(
                    activeKeyId = activeKey.keyId,
                    keys =
                        listOf(
                            PersistedArchiveKey(
                                keyId = activeKey.keyId,
                                secret = encodeSecret(activeKey.secret),
                            ),
                        ),
                ),
            ),
        )
        return ArchiveKeyState(activeKey, mapOf(activeKey.keyId to activeKey))
    }

    private fun decodeSecret(secret: String): ByteArray {
        val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(secret)
        require(decoded.size == KEY_SIZE_BYTES) { "Invalid snapshot archive key length" }
        return decoded
    }

    private fun encodeSecret(secret: ByteArray): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(secret)

    private data class ArchiveKeyState(
        val activeKey: FreezeSnapshotArchiveKey,
        val keysById: Map<String, FreezeSnapshotArchiveKey>,
    )

    private data class PersistedArchiveKeyFile(
        val activeKeyId: String,
        val keys: List<PersistedArchiveKey>,
    )

    private data class PersistedArchiveKey(
        val keyId: String,
        val secret: String,
    )

    companion object {
        private const val KEY_SIZE_BYTES = 16
    }

    private fun randomSecret(): ByteArray =
        ByteArray(KEY_SIZE_BYTES).also { bytes ->
            java.security.SecureRandom().nextBytes(bytes)
        }
}
