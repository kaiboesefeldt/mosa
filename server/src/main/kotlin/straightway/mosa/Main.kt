package straightway.mosa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    var serverStarted = false
    val parser = ArgParser("mosa")
    val serveCommand =
        ServeCommand { argumentStore ->
            serverStarted = true
            startMosaServer(argumentStore)
        }

    parser.subcommands(serveCommand)
    parser.parse(args)

    if (!serverStarted) {
        parser.parse(arrayOf("--help"))
        exitProcess(1)
    }
}

private fun startMosaServer(argumentStore: CommandLineArgumentStore) {
    val serverPort = argumentStore.getInt(ServerCommandLineArguments.SERVER_PORT) ?: 8080
    val allowHttp = argumentStore.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) ?: false
    val server =
        embeddedServer(Netty, port = serverPort) {
            install(Koin) {
                modules(module { configureMosaAppModule(argumentStore) })
            }
            installNetworkServerPlugins(getKoin().get(), !allowHttp)
            routing {
                getKoin().getAll<BaseController>().forEach { register(it) }
            }
        }

    server.start(wait = true)
}

@OptIn(ExperimentalCli::class)
internal class ServeCommand(
    private val onServe: (CommandLineArgumentStore) -> Unit,
) : Subcommand("serve", "Start the MOSA server") {
    private val serveSourceMaps by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.SERVE_SOURCE_MAPS,
            description = "Serve source maps for the client bundle",
        ).default(false)
    private val serverPort by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.SERVER_PORT,
            description = "Port for the MOSA server",
        ).default(8080)
    private val allowHttp by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.ALLOW_HTTP,
            description = "Allow plain HTTP connections to the pod",
        ).default(false)

    override fun execute() {
        val argumentValues =
            mapOf(
                ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
                ServerCommandLineArguments.SERVER_PORT to serverPort,
                ServerCommandLineArguments.ALLOW_HTTP to allowHttp,
            )
        logCommandLineArguments(argumentValues)
        onServe(CommandLineArgumentStore.from { name -> argumentValues[name] })
    }
}

private fun logCommandLineArguments(argumentValues: Map<String, Any>) {
    val formattedArguments =
        buildString {
            appendLine("Server command line options:")
            val maxKeyLength = argumentValues.keys.maxOfOrNull { it.length } ?: 0
            argumentValues
                .toSortedMap()
                .forEach { (key, value) ->
                    append("  ")
                    append(key.padEnd(maxKeyLength))
                    append(" = ")
                    appendLine(value.toString())
                }
        }
    log.info { formattedArguments }
}
