package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import straightway.mosa.crypto.CryptoEd25519

@OptIn(ExperimentalCli::class)
class CreateIdentityCommand() :
    Subcommand(
        "identity",
        "Create a new identity and write it as PEM to stdout"
    ) {
    private val pem by option(
        type = ArgType.Boolean,
        fullName = "pem",
        shortName = "p",
        description = "Output in PEM format"
    ).default(false)
    private val base64url by option(
        type = ArgType.Boolean,
        fullName = "base64urlencoded",
        shortName = "b",
        description = "Output in URL encoded base64 format without padding"
    ).default(false)

    override fun execute() {
        val crypto = CryptoEd25519()
        val keyPair = crypto.generateKeyPair()
        if (pem || (!pem && !base64url)) println(crypto.exportPrivateKeyPairToPem(keyPair))
        if (base64url) println(crypto.serializeKeyPair(keyPair))
    }
}
