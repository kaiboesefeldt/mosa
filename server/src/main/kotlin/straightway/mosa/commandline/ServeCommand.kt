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
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.commandline.CommandLineOptionsLogging.commandLineOptionsLogString
import straightway.mosa.configureMosaAppModule
import straightway.mosa.content.ContentCommandLineOptions
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.defense.installDefenseRoutingPlugins
import straightway.mosa.dev.DevCommandLineOptions
import straightway.mosa.doc.MarkdownRenderer.render
import straightway.mosa.doc.MarkdownResourceReader.readMarkdownResource
import straightway.mosa.installKtorPlugins
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.network.createServer
import straightway.mosa.vectorstore.VectorStoreCommandLineOptions
import straightway.mosa.webclient.WebClientCommandLineOptions

@OptIn(ExperimentalCli::class)
internal class ServeCommand :
    Subcommand(
        "serve",
        "Start the MOSA server",
    ) {
    private val doc by
        option(
            ArgType.Boolean,
            shortName = "d",
            fullName = "doc",
            description = "Print detailed serve documentation and exit",
        ).default(false)

    private val options =
        listOf(
            DefenseCommandLineOptions(this),
            VectorStoreCommandLineOptions(this),
            NetworkCommandLineOptions(this),
            ContentCommandLineOptions(this),
            DevCommandLineOptions(this),
            WebClientCommandLineOptions(this),
        )

    private val argumentValues by lazy {
        options.fold(emptyMap<String, Any?>()) { result, options ->
            result + options.toArgumentValues()
        }
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
                log.info {
                    argumentValues.commandLineOptionsLogString(
                        heading = "Server command line options:",
                    )
                }

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

    private companion object {
        private val log = KotlinLogging.logger {}
    }
}
