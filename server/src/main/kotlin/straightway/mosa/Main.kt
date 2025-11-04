package straightway.mosa

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import straightway.mosa.base.BaseController
import straightway.mosa.base.register

private val log = KotlinLogging.logger {}

suspend fun main(args: Array<String>) {
    val argumentStore = buildCommandLineArgumentStore(args)
    val serverPort = (argumentStore.get(ServerCommandLineArguments.SERVER_PORT) as? Int) ?: 8080
    val allowHttp =
        (argumentStore.get(ServerCommandLineArguments.ALLOW_HTTP) as? Boolean) ?: false
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

    server.startSuspend(wait = true)
}

internal fun buildCommandLineArgumentStore(args: Array<String>): CommandLineArgumentStore {
    val parser = ArgParser("mosa-server")
    val serveSourceMaps by
        parser
            .option(
                ArgType.Boolean,
                fullName = ServerCommandLineArguments.SERVE_SOURCE_MAPS_OPTION,
                description = "Serve source maps for the client bundle",
            ).default(false)
    val serverPort by
        parser
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.SERVER_PORT_OPTION,
                description = "Port for the MOSA server",
            ).default(8080)
    val allowHttp by
        parser
            .option(
                ArgType.Boolean,
                fullName = ServerCommandLineArguments.ALLOW_HTTP_OPTION,
                description = "Allow plain HTTP connections to the pod",
            ).default(false)
    parser.parse(args)
    val argumentValues =
        mapOf(
            ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
            ServerCommandLineArguments.SERVER_PORT to serverPort,
            ServerCommandLineArguments.ALLOW_HTTP to allowHttp,
        )
    logCommandLineArguments(argumentValues)
    return CommandLineArgumentStore.from { name -> argumentValues[name] }
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
