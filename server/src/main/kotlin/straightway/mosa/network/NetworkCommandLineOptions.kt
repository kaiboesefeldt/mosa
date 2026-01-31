package straightway.mosa.network

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCli::class)
class NetworkCommandLineOptions(
    command: Subcommand,
) {
    val podCertificateValiditySeconds by
        command
            .option(
                ArgType.Int,
                fullName = POD_CERT_VALIDITY_SECONDS,
                description = "Validity duration for pod certificates in seconds",
            ).default(7.days.inWholeSeconds.toInt())
    private val serverPort by
        command
            .option(
                ArgType.Int,
                fullName = SERVER_PORT,
                description = "Port for the MOSA server",
            ).default(8080)
    private val allowHttp by
        command
            .option(
                ArgType.Boolean,
                fullName = ALLOW_HTTP,
                description = "Allow plain HTTP connections to the pod",
            ).default(false)
    private val httpsPort by
        command
            .option(
                ArgType.Int,
                fullName = HTTPS_PORT,
                description = "Port for HTTPS when dev self-signed TLS is enabled",
            ).default(8443)
    private val trustedProxyIps by
        command
            .option(
                ArgType.String,
                fullName = TRUSTED_PROXY_IPS,
                description = "Comma-separated IPs of trusted proxies for forwarded headers",
            ).default("")
    private val requestReadTimeoutSeconds by
        command
            .option(
                ArgType.Int,
                fullName = REQUEST_READ_TIMEOUT_SECONDS,
                description = "Request read timeout in seconds",
            ).default(20)
    private val responseWriteTimeoutSeconds by
        command
            .option(
                ArgType.Int,
                fullName = RESPONSE_WRITE_TIMEOUT_SECONDS,
                description = "Response write timeout in seconds",
            ).default(30)

    fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            POD_CERT_VALIDITY_SECONDS to podCertificateValiditySeconds,
            SERVER_PORT to serverPort,
            ALLOW_HTTP to allowHttp,
            HTTPS_PORT to httpsPort,
            TRUSTED_PROXY_IPS to trustedProxyIps,
            REQUEST_READ_TIMEOUT_SECONDS to requestReadTimeoutSeconds,
            RESPONSE_WRITE_TIMEOUT_SECONDS to responseWriteTimeoutSeconds,
        )

    companion object {
        const val POD_CERT_VALIDITY_SECONDS = "pod-cert-validity-seconds"
        const val SERVER_PORT = "port"
        const val ALLOW_HTTP = "allow-http"
        const val HTTPS_PORT = "https-port"
        const val TRUSTED_PROXY_IPS = "trusted-proxy-ips"
        const val REQUEST_READ_TIMEOUT_SECONDS = "request-read-timeout-seconds"
        const val RESPONSE_WRITE_TIMEOUT_SECONDS = "response-write-timeout-seconds"
    }
}
