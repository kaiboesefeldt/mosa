package straightway.mosa.commandline

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.VectorStore
import straightway.mosa.configureMosaAppModule
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ExportCommand(
    private val loggingSilencer: () -> Unit = ::silenceLogging,
    private val exit: (Int) -> Nothing = ::exitProcess,
) : Subcommand("export", "Export the mosa posts and replies to stdout") {
    override fun execute() {
        loggingSilencer()
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

                    @Suppress("AssignedValueIsNeverRead")
                    notFirst = true
                }
                println("\n]")
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }
}

private fun silenceLogging() {
    val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    rootLogger.level = Level.OFF
}
