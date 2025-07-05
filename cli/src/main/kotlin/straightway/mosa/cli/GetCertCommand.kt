package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import straightway.mosa.authorization.AuthorizationRequest

@OptIn(ExperimentalCli::class)
class GetCertCommand(
    val restClient: () -> MosaRestClient,
) : Subcommand("cert", "Let the Mosa Pod approve a given gid") {
    private val gid by option(ArgType.String, fullName = "gid").required()

    override fun execute() {
        runBlocking {
            val jwt = restClient().approveGid(AuthorizationRequest(gid))
            println(jwt)
        }
    }
}