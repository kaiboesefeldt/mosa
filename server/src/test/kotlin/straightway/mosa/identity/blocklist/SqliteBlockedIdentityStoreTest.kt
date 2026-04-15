package straightway.mosa.identity.blocklist

import straightway.mosa.structuredstate.StructuredStateDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqliteBlockedIdentityStoreTest {
    @Test
    fun `block inserts gid once`() {
        withFixture { fixture ->
            assertTrue(fixture.store.block("gid-1"))
            assertFalse(fixture.store.block("gid-1"))
            assertTrue(fixture.store.isBlocked("gid-1"))
        }
    }

    @Test
    fun `unblock removes existing gid`() {
        withFixture { fixture ->
            fixture.store.block("gid-1")

            assertTrue(fixture.store.unblock("gid-1"))
            assertFalse(fixture.store.isBlocked("gid-1"))
        }
    }

    @Test
    fun `unblock returns false for missing gid`() {
        withFixture { fixture ->
            assertFalse(fixture.store.unblock("missing"))
        }
    }

    @Test
    fun `delete removes existing gid`() {
        withFixture { fixture ->
            fixture.store.block("gid-1")

            assertTrue(fixture.store.delete("gid-1"))
            assertFalse(fixture.store.isBlocked("gid-1"))
        }
    }

    @Test
    fun `table is created on initialize`() {
        withFixture { fixture ->
            assertEquals(false, fixture.store.isBlocked("unknown"))
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val directory =
            java.nio.file.Files
                .createTempDirectory("blocked-identity-store")
        private val databasePath = directory.resolve("mosa_db.sqlite")
        private val database = StructuredStateDatabase(databasePath)
        val store = SqliteBlockedIdentityStore(database)

        init {
            database.initialize(BlockedIdentitiesTable)
        }

        fun close() {
            java.nio.file.Files
                .deleteIfExists(databasePath)
            java.nio.file.Files
                .deleteIfExists(directory)
        }
    }
}
