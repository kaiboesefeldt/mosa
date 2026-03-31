package straightway.mosa.content

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path

class StructuredStateDatabase(
    private val path: Path,
) : StructuredStateContext {
    private val database: Database by lazy {
        Files.createDirectories(path.toAbsolutePath().parent)
        Database.connect(
            url = "jdbc:sqlite:${path.toAbsolutePath()}",
            driver = SQLITE_DRIVER,
        )
    }

    fun initialize(vararg tables: Table) {
        transaction(database) {
            SchemaUtils.create(*tables)
        }
    }

    override fun <T> execute(block: () -> T): T = transaction(database) { block() }

    fun <T> inTransaction(block: (StructuredStateContext) -> T): T =
        transaction(database) {
            block(
                object : StructuredStateContext {
                    override fun <T> execute(block: () -> T): T = block()
                },
            )
        }

    companion object {
        private const val SQLITE_DRIVER = "org.sqlite.JDBC"
    }
}
