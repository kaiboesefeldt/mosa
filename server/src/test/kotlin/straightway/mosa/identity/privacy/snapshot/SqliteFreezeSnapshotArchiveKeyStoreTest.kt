package straightway.mosa.identity.privacy.snapshot

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Test
import straightway.mosa.structuredstate.StructuredStateDatabase
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqliteFreezeSnapshotArchiveKeyStoreTest {
    @Test
    fun `creates and persists archive key when database is empty`() {
        withStoreDatabase { database ->
            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())

            val activeKey = sut.activeKey()

            assertEquals("archive-2026", activeKey.keyId)
            assertEquals(16, activeKey.secret.size)
            assertEquals(activeKey, sut.findByKeyId(activeKey.keyId))
            assertEquals(1, activeRowCount(database))
            assertEquals(1, totalRowCount(database))
        }
    }

    @Test
    fun `repeated first access across fresh store instances converges on one persisted active key`() {
        withStoreDatabase { database ->
            val first = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock()).activeKey()
            val second = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock()).activeKey()
            val third = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock()).activeKey()

            assertEquals(first, second)
            assertEquals(first, third)
            assertEquals(1, activeRowCount(database))
            assertEquals(1, totalRowCount(database))
        }
    }

    @Test
    fun `reuses persisted keys for active key and historical lookup after restart`() {
        withStoreDatabase { database ->
            insertKey(
                database = database,
                keyId = "archive-2027",
                secret = ByteArray(16) { it.toByte() },
                isActive = true,
                createdAt = 1_775_289_600,
            )
            insertKey(
                database = database,
                keyId = "archive-2026",
                secret = ByteArray(16) { (it + 16).toByte() },
                isActive = false,
                createdAt = 1_743_753_600,
            )

            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())
            val active = sut.activeKey()
            val historical = sut.findByKeyId("archive-2026")

            assertEquals("archive-2027", active.keyId)
            assertContentEquals(ByteArray(16) { it.toByte() }, active.secret)
            assertEquals("archive-2026", historical?.keyId)
            assertContentEquals(ByteArray(16) { (it + 16).toByte() }, historical?.secret)
            assertNull(sut.findByKeyId("archive-other"))
        }
    }

    @Test
    fun `rejects persisted key with invalid key length`() {
        withStoreDatabase { database ->
            insertKey(
                database = database,
                keyId = "archive-2026",
                secret = ByteArray(15),
                isActive = true,
                createdAt = 1_743_753_600,
            )
            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())

            val exception =
                assertFailsWith<InvalidFreezeSnapshotException> {
                    sut.activeKey()
                }

            assertEquals("Invalid snapshot archive key state", exception.message)
        }
    }

    @Test
    fun `rejects persisted key with malformed secret encoding`() {
        withStoreDatabase { database ->
            database.execute {
                SnapshotArchiveKeysTable.insert { statement ->
                    statement[keyId] = "archive-2026"
                    statement[secret] = "%%%not-base64%%%"
                    statement[isActive] = true
                    statement[createdAt] = 1_743_753_600
                }
            }
            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())

            val exception =
                assertFailsWith<InvalidFreezeSnapshotException> {
                    sut.activeKey()
                }

            assertEquals("Invalid snapshot archive key state", exception.message)
        }
    }

    @Test
    fun `rejects persisted state with zero active rows after bootstrap`() {
        withStoreDatabase { database ->
            insertKey(
                database = database,
                keyId = "archive-2026",
                secret = ByteArray(16),
                isActive = false,
                createdAt = 1_743_753_600,
            )
            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())

            val exception =
                assertFailsWith<IllegalStateException> {
                    sut.activeKey()
                }

            assertEquals("Invalid snapshot archive key state", exception.message)
        }
    }

    @Test
    fun `rejects persisted state with multiple active rows`() {
        withStoreDatabase { database ->
            dropSingleActiveIndex(database)
            insertKey(
                database = database,
                keyId = "archive-2026",
                secret = ByteArray(16),
                isActive = true,
                createdAt = 1_743_753_600,
            )
            insertKey(
                database = database,
                keyId = "archive-2027",
                secret = ByteArray(16) { (it + 1).toByte() },
                isActive = true,
                createdAt = 1_775_289_600,
            )
            val sut = SqliteFreezeSnapshotArchiveKeyStore(database, fixedClock())

            val exception =
                assertFailsWith<IllegalStateException> {
                    sut.activeKey()
                }

            assertEquals("Invalid snapshot archive key state", exception.message)
        }
    }

    private fun withStoreDatabase(block: (StructuredStateDatabase) -> Unit) {
        val directory = Files.createTempDirectory("sqlite-snapshot-archive-key-store")
        val path = directory.resolve("mosa_db.sqlite")

        try {
            val database = StructuredStateDatabase(path)
            database.initialize(SnapshotArchiveKeysTable)
            database.execute {
                SnapshotArchiveKeysTable.ensureIndexes()
            }
            block(database)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    private fun insertKey(
        database: StructuredStateDatabase,
        keyId: String,
        secret: ByteArray,
        isActive: Boolean,
        createdAt: Long,
    ) {
        database.execute {
            SnapshotArchiveKeysTable.insert { statement ->
                statement[SnapshotArchiveKeysTable.keyId] = keyId
                statement[SnapshotArchiveKeysTable.secret] =
                    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(secret)
                statement[SnapshotArchiveKeysTable.isActive] = isActive
                statement[SnapshotArchiveKeysTable.createdAt] = createdAt
            }
        }
    }

    private fun totalRowCount(database: StructuredStateDatabase): Int =
        database.execute {
            SnapshotArchiveKeysTable.selectAll().count().toInt()
        }

    private fun activeRowCount(database: StructuredStateDatabase): Int =
        database.execute {
            SnapshotArchiveKeysTable
                .selectAll()
                .where { SnapshotArchiveKeysTable.isActive eq true }
                .count()
                .toInt()
        }

    private fun dropSingleActiveIndex(database: StructuredStateDatabase) {
        database.execute {
            TransactionManager.current().exec("DROP INDEX IF EXISTS ${SnapshotArchiveKeysTable.SINGLE_ACTIVE_INDEX_NAME}")
        }
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-03-09T00:00:00Z"), ZoneOffset.UTC)
}
