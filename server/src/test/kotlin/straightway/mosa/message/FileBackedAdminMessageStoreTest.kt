package straightway.mosa.message

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileBackedAdminMessageStoreTest {
    @Test
    fun `append persists records to json file`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-store")
            val path = directory.resolve("messages.json")
            val sut = FileBackedAdminMessageStore(path)

            try {
                sut.append(
                    AdminMessageRecord(
                        id = "message-1",
                        recipientGid = "recipient-a",
                        senderGid = "admin-a",
                        createdAt = 100,
                        subject = "Subject",
                        body = "Body",
                        expiresAt = 200,
                    ),
                )

                assertTrue(Files.exists(path))
                assertTrue(Files.readString(path).contains("message-1"))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadVisibleAndShortenExpiry removes expired messages and shortens remaining ttl atomically`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-store")
            val path = directory.resolve("messages.json")
            val sut = FileBackedAdminMessageStore(path)

            try {
                sut.append(AdminMessageRecord("expired", "recipient-a", "admin-a", 1, "S1", "B1", 10))
                sut.append(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 90))
                sut.append(AdminMessageRecord("foreign", "recipient-b", "admin-a", 3, "S3", "B3", 90))

                val visible =
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 20,
                        retrievedExpiryUpperBoundEpochSeconds = 40,
                    )

                assertEquals(
                    listOf(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 40)),
                    visible,
                )
                val secondRead =
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 21,
                        retrievedExpiryUpperBoundEpochSeconds = 80,
                    )
                assertEquals(
                    listOf(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 40)),
                    secondRead,
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadVisibleForRecipient removes expired messages without shortening ttl`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-store")
            val path = directory.resolve("messages.json")
            val sut = FileBackedAdminMessageStore(path)

            try {
                sut.append(AdminMessageRecord("expired", "recipient-a", "admin-a", 1, "S1", "B1", 10))
                sut.append(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 90))
                sut.append(AdminMessageRecord("foreign", "recipient-b", "admin-a", 3, "S3", "B3", 90))

                val visible =
                    sut.loadVisibleForRecipient(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 20,
                    )

                assertEquals(
                    listOf(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 90)),
                    visible,
                )
                val secondRead =
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 21,
                        retrievedExpiryUpperBoundEpochSeconds = 40,
                    )
                assertEquals(
                    listOf(AdminMessageRecord("visible", "recipient-a", "admin-a", 2, "S2", "B2", 40)),
                    secondRead,
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `deleteExpired returns number of removed messages`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-store")
            val path = directory.resolve("messages.json")
            val sut = FileBackedAdminMessageStore(path)

            try {
                sut.append(AdminMessageRecord("expired-1", "recipient-a", "admin-a", 1, "S1", "B1", 10))
                sut.append(AdminMessageRecord("expired-2", "recipient-a", "admin-a", 2, "S2", "B2", 11))
                sut.append(AdminMessageRecord("visible", "recipient-a", "admin-a", 3, "S3", "B3", 50))

                val deleted = sut.deleteExpired(12)

                assertEquals(2, deleted)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }
}
