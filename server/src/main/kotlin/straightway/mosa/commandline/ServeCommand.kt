package straightway.mosa.commandline

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.ServerCommandLineArguments
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.configureMosaAppModule
import straightway.mosa.installNetworkServerPlugins
import straightway.mosa.tls.DevSelfSignedCertificateFactory
import straightway.mosa.tls.InMemoryKeyStore
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCli::class)
internal class ServeCommand(
    private val startServer: (CommandLineArgumentStore) -> Unit = ::startMosaServer,
    private val logArguments: (Map<String, Any>) -> Unit = ::logCommandLineArguments,
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
    private val devSelfSignedTls by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.DEV_SELF_SIGNED_TLS,
            description = "Generate an in-memory self-signed certificate for HTTPS",
        ).default(false)
    private val httpsPort by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.HTTPS_PORT,
            description = "Port for HTTPS when dev self-signed TLS is enabled",
        ).default(8443)
    private val devCertHost by
        option(
            ArgType.String,
            fullName = ServerCommandLineArguments.DEV_CERT_HOST,
            description = "Primary host or IP to include in the self-signed certificate SAN",
        ).default("localhost")

    override fun execute() {
        val argumentValues =
            mapOf(
                ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
                ServerCommandLineArguments.SERVER_PORT to serverPort,
                ServerCommandLineArguments.ALLOW_HTTP to allowHttp,
                ServerCommandLineArguments.DEV_SELF_SIGNED_TLS to devSelfSignedTls,
                ServerCommandLineArguments.HTTPS_PORT to httpsPort,
                ServerCommandLineArguments.DEV_CERT_HOST to devCertHost,
            )
        logArguments(argumentValues)
        startServer(CommandLineArgumentStore.from { name -> argumentValues[name] })
    }
}

private fun startMosaServer(argumentStore: CommandLineArgumentStore) {
    val serverPort = argumentStore.getInt(ServerCommandLineArguments.SERVER_PORT) ?: 8080
    val allowHttp = argumentStore.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) ?: false
    val requireTlsForPod = !allowHttp
    val enableSelfSignedTls = argumentStore.getBoolean(ServerCommandLineArguments.DEV_SELF_SIGNED_TLS) ?: false
    val httpsPort = argumentStore.getInt(ServerCommandLineArguments.HTTPS_PORT) ?: 8443
    val devCertHost = argumentStore.getString(ServerCommandLineArguments.DEV_CERT_HOST) ?: "localhost"
    val mosaModule: Application.() -> Unit = {
        install(Koin) {
            modules(module { configureMosaAppModule(argumentStore) })
        }
        installNetworkServerPlugins(getKoin().get(), requireTlsForPod)
        routing {
            getKoin().getAll<BaseController>().forEach { register(it) }
        }
    }
    val server =
        if (enableSelfSignedTls) {
            val keyStore = DevSelfSignedCertificateFactory().create(devCertHost)
            val connectors = devConnectors(allowHttp, serverPort, httpsPort, keyStore)
            CoroutineScope(Dispatchers.IO)
                .embeddedServer(
                    factory = Netty,
                    connectors = connectors.toTypedArray(),
                    watchPaths = emptyList(),
                    parentCoroutineContext = EmptyCoroutineContext,
                    module = mosaModule,
                )
        } else {
            embeddedServer(Netty, port = serverPort, module = mosaModule)
        }

    server.start(wait = true)
}

private fun devConnectors(
    allowHttp: Boolean,
    httpPort: Int,
    httpsPort: Int,
    keyStore: InMemoryKeyStore,
): List<EngineConnectorConfig> {
    val connectors = mutableListOf<EngineConnectorConfig>()
    if (allowHttp) {
        connectors.add(EngineConnectorBuilder().apply { port = httpPort })
    }
    connectors.add(
        EngineSSLConnectorBuilder(
            keyStore.keyStore,
            keyStore.alias,
            { keyStore.password },
            { keyStore.password },
        ).apply { port = httpsPort },
    )
    return connectors
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

private val log = KotlinLogging.logger {}
