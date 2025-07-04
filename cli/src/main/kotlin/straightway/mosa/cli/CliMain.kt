package straightway.mosa.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.default
import java.net.URI

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("MOSA CLI")
    val userGID by parser
        .option(
            ArgType.String,
            shortName = "u",
            description = "The Global ID (gid) of the MOSA user",
        ).default("defaultUserGID")
    val pod by parser
        .option(
            ArgType.String,
            fullName = "pod",
            shortName = "p",
        ).default("http://localhost:8080")

    fun mosaRestClient() =
        MosaRestClient(
            podUrl = URI(pod).toURL(),
            credentials = userGID,
            httpClient =
                HttpClient(CIO) {
                    install(ContentNegotiation) {
                        jackson()
                    }
                }
        )

    parser.subcommands(PublishCommand { mosaRestClient() })
    parser.subcommands(SearchCommand { mosaRestClient() })

    val parseResult = parser.parse(args)

    if (parseResult.commandName.isBlank()) {
        parser.parse(arrayOf("--help"))
        kotlin.system.exitProcess(1)
    }
}
