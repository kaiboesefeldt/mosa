package straightway.mosa.commandline

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.ServerCommandLineOptions
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.configureMosaAppModule
import straightway.mosa.content.ContentCommandLineOptions
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.defense.installDefenseRoutingPlugins
import straightway.mosa.doc.MarkdownRenderer.render
import straightway.mosa.doc.MarkdownResourceReader.readMarkdownResource
import straightway.mosa.installKtorPlugins
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.network.createServer
import straightway.mosa.vectorstore.VectorStoreCommandLineOptions

@OptIn(ExperimentalCli::class)
internal class ServeCommand :
    Subcommand(
        "serve",
        "Start the MOSA server",
    ) {
    private val serveSourceMaps by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineOptions.SERVE_SOURCE_MAPS,
            description = "Serve source maps for the client bundle",
        ).default(false)
    private val doc by
        option(
            ArgType.Boolean,
            shortName = "d",
            fullName = "doc",
            description = "Print detailed serve documentation and exit",
        ).default(false)
    private val devSelfSignedTls by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineOptions.DEV_SELF_SIGNED_TLS,
            description = "Generate an in-memory self-signed certificate for HTTPS",
        ).default(false)
    private val devCertHost by
        option(
            ArgType.String,
            fullName = ServerCommandLineOptions.DEV_CERT_HOST,
            description = "Primary host or IP to include in the self-signed certificate SAN",
        ).default("localhost")
    private val defenseOptions = DefenseCommandLineOptions(this)
    private val vectorStoreOptions = VectorStoreCommandLineOptions(this)
    private val networkOptions = NetworkCommandLineOptions(this)
    private val contentOptions = ContentCommandLineOptions(this)

    private val argumentValues by lazy {
        mapOf(
            ServerCommandLineOptions.SERVE_SOURCE_MAPS to serveSourceMaps,
            ServerCommandLineOptions.DEV_SELF_SIGNED_TLS to devSelfSignedTls,
            ServerCommandLineOptions.DEV_CERT_HOST to devCertHost,
        ) +
            defenseOptions.toArgumentValues() +
            vectorStoreOptions.toArgumentValues() +
            networkOptions.toArgumentValues() +
            contentOptions.toArgumentValues()
    }

    override fun execute() {
        when {
            doc -> render(readMarkdownResource("/doc/serve/index.md"))
            else -> startMosaServer(CommandLineArgumentStore.from { name -> argumentValues[name] })
        }
    }

    internal fun startMosaServer(argumentStore: CommandLineArgumentStore) {
        val mosaModule: Application.() -> Unit = {
            install(Koin) {
                modules(
                    module {
                        configureMosaAppModule(argumentStore)
                        configureDefenseAppModule()
                        configureNetworkAppModule()
                    },
                )
            }
            with(koin()) {
                logCommandLineArguments()

                installKtorPlugins()
                routing {
                    installDefenseRoutingPlugins()
                    getAll<BaseController>().forEach { register(it) }
                }
            }
        }

        val server = createServer(argumentStore, mosaModule)

        server.start(wait = true)
    }

    private fun logCommandLineArguments() {
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
}

private val log = KotlinLogging.logger {}
