package straightway.mosa.auth

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCli::class)
class AuthCommandLineOptions(
    command: Subcommand,
) {
    val podCertificateValiditySeconds by
        command
            .option(
                ArgType.Int,
                fullName = POD_CERT_VALIDITY_SECONDS,
                description = "Validity duration for pod certificates in seconds",
            ).default(7.days.inWholeSeconds.toInt())

    fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            POD_CERT_VALIDITY_SECONDS to podCertificateValiditySeconds,
        )

    companion object {
        const val POD_CERT_VALIDITY_SECONDS = "pod-cert-validity-seconds"
    }
}
