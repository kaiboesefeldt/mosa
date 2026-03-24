package straightway.mosa.content

import org.junit.jupiter.api.Test
import straightway.mosa.message.AdminMessagesTable
import java.nio.file.Files
import java.sql.DriverManager
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
}
