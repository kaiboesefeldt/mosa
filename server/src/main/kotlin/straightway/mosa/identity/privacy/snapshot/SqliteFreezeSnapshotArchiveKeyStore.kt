package straightway.mosa.identity.privacy.snapshot

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import straightway.mosa.structuredstate.StructuredStateDatabase
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset
import kotlin.io.encoding.Base64

class SqliteFreezeSnapshotArchiveKeyStore(
    private val database: StructuredStateDatabase,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom(),
) : FreezeSnapshotArchiveKeyStore,
    FreezeSnapshotActiveArchiveKeyProvider {
    override fun activeKey(): FreezeSnapshotArchiveKey =
        database.inTransaction {
            loadActiveKeys().singleOrNull()
                ?: bootstrapAndLoadActiveKey()
        }

    override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? =
        database.execute {
            SnapshotArchiveKeysTable
                .selectAll()
                .where { SnapshotArchiveKeysTable.keyId eq keyId }
                .limit(1)
                .map(::toArchiveKey)
                .singleOrNull()
        }

    private fun bootstrapAndLoadActiveKey(): FreezeSnapshotArchiveKey {
        if (hasAnyPersistedKey()) {
            throw invalidState()
        }
        val initialKey = newArchiveKey()
        SnapshotArchiveKeysTable.insertIgnore { statement ->
            statement[keyId] = initialKey.keyId
            statement[secret] = encodeSecret(initialKey.secret)
            statement[isActive] = true
            statement[createdAt] = clock.instant().epochSecond
        }
        return loadActiveKeys().singleOrNull() ?: throw invalidState()
    }

    private fun hasAnyPersistedKey(): Boolean =
        SnapshotArchiveKeysTable
            .selectAll()
            .limit(1)
            .any()

    private fun loadActiveKeys(): List<FreezeSnapshotArchiveKey> =
        SnapshotArchiveKeysTable
            .selectAll()
            .where { SnapshotArchiveKeysTable.isActive eq true }
            .map(::toArchiveKey)
            .also { keys ->
                if (1 < keys.size) {
                    throw invalidState()
                }
            }

    private fun toArchiveKey(row: org.jetbrains.exposed.v1.core.ResultRow): FreezeSnapshotArchiveKey =
        FreezeSnapshotArchiveKey(
            keyId = row[SnapshotArchiveKeysTable.keyId],
            secret = decodeSecret(row[SnapshotArchiveKeysTable.secret]),
        )

    private fun newArchiveKey(): FreezeSnapshotArchiveKey =
        FreezeSnapshotArchiveKey(
            keyId = "archive-${clock.instant().atZone(ZoneOffset.UTC).year}",
            secret =
                ByteArray(KEY_SIZE_BYTES).also { bytes ->
                    random.nextBytes(bytes)
                },
        )

    private fun decodeSecret(secret: String): ByteArray =
        try {
            val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(secret)
            require(decoded.size == KEY_SIZE_BYTES) { "Invalid snapshot archive key length" }
            decoded
        } catch (exception: IllegalArgumentException) {
            throw InvalidFreezeSnapshotException("Invalid snapshot archive key state", exception)
        }

    private fun encodeSecret(secret: ByteArray): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(secret)

    private fun invalidState(): IllegalStateException = IllegalStateException("Invalid snapshot archive key state")

    private companion object {
        const val KEY_SIZE_BYTES = 16
    }
}
