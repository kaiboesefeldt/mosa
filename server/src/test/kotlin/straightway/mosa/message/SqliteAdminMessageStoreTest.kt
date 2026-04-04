package straightway.mosa.message

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.content.StructuredStateDatabase
import java.nio.file.Files
import kotlin.test.assertEquals

class SqliteAdminMessageStoreTest {
    @Test
    fun `append stores visible message for recipient`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

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

                assertEquals(
                    listOf(
                        AdminMessageRecord(
                            id = "message-1",
                            recipientGid = "recipient-a",
                            senderGid = "admin-a",
                            createdAt = 100,
                            subject = "Subject",
                            body = "Body",
                            expiresAt = 200,
                        ),
                    ),
                    sut.loadVisibleForRecipient(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 150,
                    ),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadVisibleAndShortenExpiry removes expired messages and shortens remaining ttl atomically`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

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
    fun `loadForIdentity returns messages from both message directions`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

                sut.append(AdminMessageRecord("message-to-owner", "owner", "admin", 10, "Subject 1", "Body 1", 100))
                sut.append(AdminMessageRecord("message-from-owner", "user", "owner", 20, "Subject 2", "Body 2", 100))
                sut.append(AdminMessageRecord("message-foreign", "other", "admin", 30, "Subject 3", "Body 3", 100))

                assertEquals(listOf("message-to-owner", "message-from-owner"), sut.loadForIdentity("owner").map(AdminMessageRecord::id))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `deleteByIds removes exactly matching messages`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

                sut.append(AdminMessageRecord("message-1", "owner", "admin", 10, "Subject 1", "Body 1", 100))
                sut.append(AdminMessageRecord("message-2", "owner", "admin", 20, "Subject 2", "Body 2", 100))

                assertEquals(1, sut.deleteByIds(listOf("message-1")))
                assertEquals(listOf("message-2"), sut.loadForIdentity("owner").map(AdminMessageRecord::id))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadVisibleAndShortenExpiry filters by fromTime and shortens only returned messages`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

                sut.append(AdminMessageRecord("older", "recipient-a", "admin-a", 2, "S1", "B1", 90))
                sut.append(AdminMessageRecord("boundary", "recipient-a", "admin-a", 20, "S2", "B2", 90))
                sut.append(AdminMessageRecord("newer", "recipient-a", "admin-a", 21, "S3", "B3", 90))

                val visible =
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 10,
                        retrievedExpiryUpperBoundEpochSeconds = 40,
                        fromTime = 20,
                    )

                assertEquals(
                    listOf(
                        AdminMessageRecord("boundary", "recipient-a", "admin-a", 20, "S2", "B2", 40),
                        AdminMessageRecord("newer", "recipient-a", "admin-a", 21, "S3", "B3", 40),
                    ),
                    visible,
                )

                val secondRead =
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 11,
                        retrievedExpiryUpperBoundEpochSeconds = 80,
                    )

                assertEquals(
                    listOf(
                        AdminMessageRecord("older", "recipient-a", "admin-a", 2, "S1", "B1", 80),
                        AdminMessageRecord("boundary", "recipient-a", "admin-a", 20, "S2", "B2", 40),
                        AdminMessageRecord("newer", "recipient-a", "admin-a", 21, "S3", "B3", 40),
                    ),
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
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

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
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

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

    @Test
    fun `deleteForIdentity removes messages by recipient or sender gid`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

                sut.append(AdminMessageRecord("recipient-match", "target-gid", "admin-a", 1, "S1", "B1", 100))
                sut.append(AdminMessageRecord("sender-match", "recipient-a", "target-gid", 2, "S2", "B2", 100))
                sut.append(AdminMessageRecord("unaffected", "recipient-b", "admin-b", 3, "S3", "B3", 100))

                val deleted = sut.deleteForIdentity("target-gid")

                assertEquals(2, deleted)
                assertEquals(
                    listOf(AdminMessageRecord("unaffected", "recipient-b", "admin-b", 3, "S3", "B3", 90)),
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-b",
                        nowEpochSeconds = 10,
                        retrievedExpiryUpperBoundEpochSeconds = 90,
                    ),
                )
                assertEquals(
                    emptyList(),
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "target-gid",
                        nowEpochSeconds = 10,
                        retrievedExpiryUpperBoundEpochSeconds = 90,
                    ),
                )
                assertEquals(
                    emptyList(),
                    sut.loadVisibleAndShortenExpiry(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 10,
                        retrievedExpiryUpperBoundEpochSeconds = 90,
                    ),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `legacy json file is ignored`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val dbPath = directory.resolve("mosa_db.sqlite")
            val legacyPath = directory.resolve("mosa_admin_messages.json")

            try {
                Files.writeString(
                    legacyPath,
                    """
                    [
                      {
                        "id":"legacy-1",
                        "recipientGid":"recipient-a",
                        "senderGid":"admin-a",
                        "createdAt":1,
                        "subject":"Subject",
                        "body":"Body",
                        "expiresAt":200
                      }
                    ]
                    """.trimIndent(),
                )
                val database = StructuredStateDatabase(dbPath)
                database.initialize(AdminMessagesTable)
                val sut = SqliteAdminMessageStore(database)

                assertEquals(
                    emptyList(),
                    sut.loadVisibleForRecipient(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 20,
                    ),
                )
            } finally {
                Files.deleteIfExists(legacyPath)
                Files.deleteIfExists(dbPath)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `append rolls back with outer database transaction`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-admin-message-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(AdminMessagesTable)

                kotlin.test.assertFailsWith<IllegalStateException> {
                    database.inTransaction { context ->
                        val sut = SqliteAdminMessageStore(context)
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
                        throw IllegalStateException("boom")
                    }
                }

                val visible =
                    SqliteAdminMessageStore(database).loadVisibleForRecipient(
                        recipientGid = "recipient-a",
                        nowEpochSeconds = 150,
                    )

                assertEquals(emptyList(), visible)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }
}
