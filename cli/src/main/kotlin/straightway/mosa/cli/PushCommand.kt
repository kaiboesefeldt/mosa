package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.required

@OptIn(ExperimentalCli::class)
class PushCommand(userGID: () -> String) : Subcommand("push", "Push new content to a MOSA pod") {
    private val userGID by lazy(userGID)
    private val text by option(ArgType.String, fullName = "text", shortName = "t").required()
    private val pod by option(ArgType.String, fullName = "pod", shortName = "p").default("http://localhost:8080")

    override fun execute() {
        println("user $userGID sends text '$text' to pod $pod")
    }
}