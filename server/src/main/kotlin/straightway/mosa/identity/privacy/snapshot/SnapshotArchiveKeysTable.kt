package straightway.mosa.identity.privacy.snapshot

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

private const val SNAPSHOT_ARCHIVE_KEYS_TABLE_NAME = "snapshot_archive_keys"

object SnapshotArchiveKeysTable : Table(SNAPSHOT_ARCHIVE_KEYS_TABLE_NAME) {
    const val SINGLE_ACTIVE_INDEX_NAME = "snapshot_archive_keys_single_active_idx"

    val keyId = varchar("key_id", length = 128)
    val secret = text("secret")
    val isActive = bool("is_active")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(keyId)

    fun ensureIndexes() {
        TransactionManager.current().exec(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS $SINGLE_ACTIVE_INDEX_NAME
            ON $SNAPSHOT_ARCHIVE_KEYS_TABLE_NAME (is_active)
            WHERE is_active = 1
            """.trimIndent(),
        )
    }
}
