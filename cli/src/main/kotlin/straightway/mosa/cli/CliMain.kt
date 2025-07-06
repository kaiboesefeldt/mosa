package straightway.mosa.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.default
import straightway.mosa.crypto.CryptoEd25519
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import kotlin.io.encoding.Base64

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("MOSA CLI")
    val userGIDParam by parser
        .option(
            ArgType.String,
            shortName = "u",
            description = "The Global ID (gid) of the MOSA user",
        )
    val keyPairParam by parser
        .option(
            type = ArgType.String,
            shortName = "k",
            fullName = "keypair",
            description = "Base 64 URL encoding of the Ed25519 key pair to use for signing"
        )
    val keyPair = keyPairParam?.keyPairFromBase64() ?: createTemporaryKeyPair()
    val userGID = userGIDParam ?: keyPair.public.extractGid()
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
    parser.subcommands(GetCertCommand { mosaRestClient() })
    parser.subcommands(CreateIdentityCommand())

    val parseResult = parser.parse(args)

    if (parseResult.commandName.isBlank()) {
        parser.parse(arrayOf("--help"))
        kotlin.system.exitProcess(1)
    }
}

private val crypto = CryptoEd25519()

private fun String.keyPairFromBase64(): KeyPair = crypto.deserializeKeyPair(this)

private fun createTemporaryKeyPair(): KeyPair = crypto.generateKeyPair()

private fun PublicKey.extractGid() =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encoded)
