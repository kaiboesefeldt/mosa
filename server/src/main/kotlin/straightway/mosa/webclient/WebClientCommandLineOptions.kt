package straightway.mosa.webclient

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import straightway.mosa.CommandLineOptions

@OptIn(ExperimentalCli::class)
class WebClientCommandLineOptions(
    command: Subcommand,
) : CommandLineOptions {
    private val serveSourceMaps by
        command
            .option(
                ArgType.Boolean,
                fullName = SERVE_SOURCE_MAPS,
                description = "Serve source maps for the client bundle",
            ).default(false)

    override fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            SERVE_SOURCE_MAPS to serveSourceMaps,
        )

    companion object {
        const val SERVE_SOURCE_MAPS = "serve-source-maps"
    }
}
