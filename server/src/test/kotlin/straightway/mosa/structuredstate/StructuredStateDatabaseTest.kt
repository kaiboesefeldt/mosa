package straightway.mosa.structuredstate

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Test
import straightway.mosa.message.AdminMessagesTable
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StructuredStateDatabaseTest {
    @Test
    fun `initialize creates sqlite file and admin message table`() {
        val directory = Files.createTempDirectory("structured-state-db")
        val path = directory.resolve("mosa_db.sqlite")

        try {
            val sut = StructuredStateDatabase(path)

            sut.initialize(AdminMessagesTable)

            assertTrue(Files.exists(path))
            DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
                connection.metaData.getTables(null, null, "admin_messages", null).use { tables ->
                    assertTrue(tables.next())
                }
            }
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `inTransaction commits changes when block completes`() =
        runTest {
            val directory = Files.createTempDirectory("structured-state-db")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val sut = StructuredStateDatabase(path)
                sut.initialize(AdminMessagesTable)

                sut.inTransaction { context ->
                    context.execute {
                        AdminMessagesTable.insert { statement ->
                            statement[id] = "message-1"
                            statement[recipientGid] = "recipient-1"
                            statement[senderGid] = "admin-1"
                            statement[createdAt] = 1
                            statement[subject] = "Subject"
                            statement[body] = "Body"
                            statement[expiresAt] = 2
                            statement[deliveryKey] = "delivery-1"
                        }
                    }
                }

                val count =
                    sut.execute {
                        AdminMessagesTable.selectAll().count()
                    }

                assertEquals(1, count)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `inTransaction rolls changes back when block fails`() =
        runTest {
            val directory = Files.createTempDirectory("structured-state-db")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val sut = StructuredStateDatabase(path)
                sut.initialize(AdminMessagesTable)

                assertFailsWith<IllegalStateException> {
                    sut.inTransaction { context ->
                        context.execute {
                            AdminMessagesTable.insert { statement ->
                                statement[id] = "message-1"
                                statement[recipientGid] = "recipient-1"
                                statement[senderGid] = "admin-1"
                                statement[createdAt] = 1
                                statement[subject] = "Subject"
                                statement[body] = "Body"
                                statement[expiresAt] = 2
                                statement[deliveryKey] = "delivery-1"
                            }
                        }
                        throw IllegalStateException("boom")
                    }
                }

                val count =
                    sut.execute {
                        AdminMessagesTable.selectAll().count()
                    }

                assertEquals(0, count)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }
}
