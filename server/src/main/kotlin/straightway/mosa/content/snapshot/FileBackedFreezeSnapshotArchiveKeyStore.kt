package straightway.mosa.content.snapshot

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
    private val key: FreezeSnapshotArchiveKey by lazy { loadOrCreateKey() }

    override fun activeKey(): FreezeSnapshotArchiveKey = key

    override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = key.takeIf { it.keyId == keyId }

    private fun loadOrCreateKey(): FreezeSnapshotArchiveKey =
        if (Files.exists(path)) {
            readKey()
        } else {
            createKey()
        }

    private fun readKey(): FreezeSnapshotArchiveKey {
        val lines = Files.readAllLines(path)
        require(lines.size == 2) { "Invalid snapshot archive key file" }
        val secret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(lines[1])
        require(secret.size == KEY_SIZE_BYTES) { "Invalid snapshot archive key length" }
        return FreezeSnapshotArchiveKey(lines[0], secret)
    }

    private fun createKey(): FreezeSnapshotArchiveKey {
        Files.createDirectories(path.parent ?: Path.of("."))
        val key =
            FreezeSnapshotArchiveKey(
                keyId = "archive-${clock.instant().atZone(ZoneOffset.UTC).year}",
                secret = randomSecret(),
            )
        Files.write(
            path,
            listOf(
                key.keyId,
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(key.secret),
            ),
        )
        return key
    }

    private fun randomSecret(): ByteArray =
        ByteArray(KEY_SIZE_BYTES).also { bytes ->
            java.security.SecureRandom().nextBytes(bytes)
        }

    private companion object {
        private const val KEY_SIZE_BYTES = 16
    }
}
