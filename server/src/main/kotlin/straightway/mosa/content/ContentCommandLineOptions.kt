package straightway.mosa.content

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import straightway.mosa.CommandLineOptions

@OptIn(ExperimentalCli::class)
class ContentCommandLineOptions(
    command: Subcommand,
) : CommandLineOptions {
    private val maxFetchItems by
        command
            .option(
                ArgType.Int,
                fullName = MAX_FETCH_ITEMS,
                description = "Maximum number of items allowed per fetch request",
            ).default(100)

    override fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            MAX_FETCH_ITEMS to maxFetchItems,
        )

    companion object {
        const val MAX_FETCH_ITEMS = "max-fetch-items"
    }
}
