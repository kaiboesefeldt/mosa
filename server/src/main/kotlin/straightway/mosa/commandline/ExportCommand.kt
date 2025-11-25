package straightway.mosa.commandline

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.VectorStore
import straightway.mosa.configureMosaAppModule
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ExportCommand : Subcommand("export", "Export the mosa posts and replies to stdout") {
    override fun execute() {
        val koinApplication =
            startKoin {
                modules(module { configureMosaAppModule(CommandLineArgumentStore.EMPTY) })
            }
        val vectorStore = koinApplication.koin.get<VectorStore>()
        val objectMapper = ObjectMapper()
        try {
            runBlocking {
                print("[")
                var notFirst = false
                vectorStore.dumpAll().collect {
                    println(if (notFirst) "," else "")
                    print(objectMapper.writeValueAsString(it))
                    notFirst = true
                }
                println("\n]")
            }
        } finally {
            koinApplication.close()
        }
        exitProcess(0)
    }
}
