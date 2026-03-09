package straightway.mosa.content.snapshot

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileBackedFreezeSnapshotArchiveKeyStoreTest {
    @Test
    fun `creates and persists archive key when file is missing`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-key.txt")
        val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

        try {
            val activeKey = sut.activeKey()

            assertEquals("archive-2026", activeKey.keyId)
            assertEquals(16, activeKey.secret.size)
            assertTrue(Files.exists(path))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `reuses persisted key for lookup and active key`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-key.txt")
        val first = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

        try {
            val created = first.activeKey()
            val second = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())
            val loaded = second.activeKey()

            assertEquals(created.keyId, loaded.keyId)
            assertContentEquals(created.secret, loaded.secret)
            assertEquals(loaded, second.findByKeyId(loaded.keyId))
            assertNull(second.findByKeyId("archive-other"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `rejects malformed archive key file`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-key.txt")
        Files.write(path, listOf("archive-2026"))

        try {
            val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

            assertFailsWith<IllegalArgumentException> {
                sut.activeKey()
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `rejects archive key file with invalid key length`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-key.txt")
        val invalidSecret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(15))
        Files.write(path, listOf("archive-2026", invalidSecret))

        try {
            val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

            assertFailsWith<IllegalArgumentException> {
                sut.activeKey()
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `creates archive key for relative path without parent`() {
        val path = Path.of("build", "tmp", "relative-archive-key.txt").fileName

        try {
            val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

            sut.activeKey()

            assertTrue(Files.exists(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-03-09T00:00:00Z"), ZoneOffset.UTC)
}
