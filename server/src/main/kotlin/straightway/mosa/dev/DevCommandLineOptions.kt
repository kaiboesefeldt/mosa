package straightway.mosa.dev

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import straightway.mosa.CommandLineOptions

@OptIn(ExperimentalCli::class)
class DevCommandLineOptions(
    command: Subcommand,
) : CommandLineOptions {
    private val serveSourceMaps by
        command
            .option(
                ArgType.Boolean,
                fullName = SERVE_SOURCE_MAPS,
                description = "Serve source maps for the client bundle",
            ).default(false)
    private val devSelfSignedTls by
        command
            .option(
                ArgType.Boolean,
                fullName = DEV_SELF_SIGNED_TLS,
                description = "Generate an in-memory self-signed certificate for HTTPS",
            ).default(false)
    private val devCertHost by
        command
            .option(
                ArgType.String,
                fullName = DEV_CERT_HOST,
                description = "Primary host or IP to include in the self-signed certificate SAN",
            ).default("localhost")

    override fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            SERVE_SOURCE_MAPS to serveSourceMaps,
            DEV_SELF_SIGNED_TLS to devSelfSignedTls,
            DEV_CERT_HOST to devCertHost,
        )

    companion object {
        const val SERVE_SOURCE_MAPS = "serve-source-maps"
        const val DEV_SELF_SIGNED_TLS = "dev-self-signed-tls"
        const val DEV_CERT_HOST = "dev-cert-host"
    }
}
