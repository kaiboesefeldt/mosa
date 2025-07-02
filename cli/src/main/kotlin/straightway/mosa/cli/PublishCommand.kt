package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class PublishCommand(
    val restClient: () -> MosaRestClient,
) : Subcommand("push", "Push new content to a MOSA pod") {
    private val text by option(ArgType.String, fullName = "text", shortName = "t").required()

    override fun execute() {
        runBlocking {
            restClient().publish(text)
        }
    }
}
