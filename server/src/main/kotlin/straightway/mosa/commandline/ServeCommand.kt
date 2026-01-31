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
import straightway.mosa.ServerCommandLineArguments
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.configureMosaAppModule
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.defense.installDefenseRoutingPlugins
import straightway.mosa.doc.ServeDocRenderer
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
            fullName = ServerCommandLineArguments.SERVE_SOURCE_MAPS,
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
            fullName = ServerCommandLineArguments.DEV_SELF_SIGNED_TLS,
            description = "Generate an in-memory self-signed certificate for HTTPS",
        ).default(false)
    private val devCertHost by
        option(
            ArgType.String,
            fullName = ServerCommandLineArguments.DEV_CERT_HOST,
            description = "Primary host or IP to include in the self-signed certificate SAN",
        ).default("localhost")
    private val maxFetchItems by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.MAX_FETCH_ITEMS,
            description = "Maximum number of items allowed per fetch request",
        ).default(100)
    private val defenseOptions = DefenseCommandLineOptions(this)
    private val vectorStoreOptions = VectorStoreCommandLineOptions(this)
    private val networkOptions = NetworkCommandLineOptions(this)

    private val argumentValues by lazy {
        mapOf(
            ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
            ServerCommandLineArguments.DEV_SELF_SIGNED_TLS to devSelfSignedTls,
            ServerCommandLineArguments.DEV_CERT_HOST to devCertHost,
            ServerCommandLineArguments.MAX_FETCH_ITEMS to maxFetchItems,
        ) +
            defenseOptions.toArgumentValues() +
            vectorStoreOptions.toArgumentValues() +
            networkOptions.toArgumentValues()
    }

    override fun execute() {
        if (doc) {
            ServeDocRenderer().render(readServeDocumentation())
            return
        }
        logCommandLineArguments()
        startMosaServer(CommandLineArgumentStore.from { name -> argumentValues[name] })
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

private fun readServeDocumentation(): String = readMarkdownResource("/doc/serve/index.md")

private fun readMarkdownResource(path: String): String {
    val content =
        ServeCommand::class.java
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return "Missing serve documentation resource: ${path.removePrefix("/")}"
    return expandMarkdownIncludes(content)
}

private fun expandMarkdownIncludes(markdown: String): String {
    val includePattern = Regex("\\{\\{include:([^}]+)}}")
    return buildString {
        markdown.lineSequence().forEach { line ->
            val match = includePattern.matchEntire(line.trim())
            if (match == null) {
                appendLine(line)
            } else {
                val includePath = match.groupValues[1].trim()
                appendLine(readMarkdownResource(includePath))
            }
        }
    }.trimEnd()
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

private val log = KotlinLogging.logger {}
