package straightway.mosa.commandline

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.VectorStore
import straightway.mosa.configureMosaAppModule
import java.io.OutputStream
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ExportCommand(
    private val streamIO: PostDumpStreamIO,
    private val outputStream: OutputStream,
    private val exit: (Int) -> Nothing = ::exitProcess,
) : Subcommand("export", "Export the mosa posts and replies to stdout") {
    override fun execute() {
        val koinApplication =
            startKoin {
                modules(module { configureMosaAppModule(CommandLineArgumentStore.EMPTY) })
            }
        val vectorStore = koinApplication.koin.get<VectorStore>()
        try {
            outputStream.bufferedWriter().use { stdout ->
                val writer = streamIO.writerTo(stdout)
                runBlocking { writer.writeAll(vectorStore.dumpAll()) }
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }
}
