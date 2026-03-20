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
        val path = directory.resolve("archive-keys.json")
        val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())

        try {
            val activeKey = sut.activeKey()

            assertEquals("archive-2026", activeKey.keyId)
            assertEquals(16, activeKey.secret.size)
            assertTrue(Files.exists(path))
            assertEquals(activeKey, sut.findByKeyId(activeKey.keyId))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `reuses persisted keys for active key and historical lookup`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-keys.json")
        val activeSecret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(16) { it.toByte() })
        val historicalSecret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(16) { (it + 16).toByte() })
        Files.writeString(
            path,
            """
            {
              "activeKeyId": "archive-2027",
              "keys": [
                {"keyId": "archive-2027", "secret": "$activeSecret"},
                {"keyId": "archive-2026", "secret": "$historicalSecret"}
              ]
            }
            """.trimIndent(),
        )

        try {
            val sut = FileBackedFreezeSnapshotArchiveKeyStore(path, fixedClock())
            val active = sut.activeKey()
            val historical = sut.findByKeyId("archive-2026")

            assertEquals("archive-2027", active.keyId)
            assertContentEquals(ByteArray(16) { it.toByte() }, active.secret)
            assertEquals("archive-2026", historical?.keyId)
            assertContentEquals(ByteArray(16) { (it + 16).toByte() }, historical?.secret)
            assertNull(sut.findByKeyId("archive-other"))
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `rejects legacy archive key file format`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-keys.json")
        Files.write(path, listOf("archive-2026", Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(16))))

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
        val path = directory.resolve("archive-keys.json")
        val invalidSecret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(15))
        Files.writeString(
            path,
            """
            {
              "activeKeyId": "archive-2026",
              "keys": [
                {"keyId": "archive-2026", "secret": "$invalidSecret"}
              ]
            }
            """.trimIndent(),
        )

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
    fun `rejects archive key file when active key is missing from key list`() {
        val directory = Files.createTempDirectory("snapshot-key-store")
        val path = directory.resolve("archive-keys.json")
        val secret = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(16))
        Files.writeString(
            path,
            """
            {
              "activeKeyId": "archive-2027",
              "keys": [
                {"keyId": "archive-2026", "secret": "$secret"}
              ]
            }
            """.trimIndent(),
        )

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
        val path = Path.of("build", "tmp", "relative-archive-keys.json").fileName

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
