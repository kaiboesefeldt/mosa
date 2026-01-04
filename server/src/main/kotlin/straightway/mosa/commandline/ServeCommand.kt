package straightway.mosa.commandline

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.ServerCommandLineArguments
import straightway.mosa.authenticatedRateLimit
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.configureMosaAppModule
import straightway.mosa.createMosaAuthenticatedRateLimitPlugin
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.installNetworkServerPlugins
import straightway.mosa.tls.DevSelfSignedCertificateFactory
import straightway.mosa.tls.InMemoryKeyStore
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCli::class)
internal class ServeCommand(
    private val startServer: (CommandLineArgumentStore) -> Unit = ::startMosaServer,
    private val logArguments: (Map<String, Any>) -> Unit = ::logCommandLineArguments,
) : Subcommand("serve", "Start the MOSA server") {
    private val serveSourceMaps by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.SERVE_SOURCE_MAPS,
            description = "Serve source maps for the client bundle",
        ).default(false)
    private val serverPort by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.SERVER_PORT,
            description = "Port for the MOSA server",
        ).default(8080)
    private val allowHttp by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.ALLOW_HTTP,
            description = "Allow plain HTTP connections to the pod",
        ).default(false)
    private val devSelfSignedTls by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.DEV_SELF_SIGNED_TLS,
            description = "Generate an in-memory self-signed certificate for HTTPS",
        ).default(false)
    private val httpsPort by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.HTTPS_PORT,
            description = "Port for HTTPS when dev self-signed TLS is enabled",
        ).default(8443)
    private val devCertHost by
        option(
            ArgType.String,
            fullName = ServerCommandLineArguments.DEV_CERT_HOST,
            description = "Primary host or IP to include in the self-signed certificate SAN",
        ).default("localhost")
    private val loadSheddingStart by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.LOAD_SHEDDING_START,
            description = "Normalized CPU load where shedding starts (0.0-1.0)",
        ).default(0.85)
    private val loadSheddingFull by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.LOAD_SHEDDING_FULL,
            description = "Normalized CPU load where shedding is full (0.0-1.0)",
        ).default(0.95)
    private val memorySheddingStart by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.MEMORY_SHEDDING_START,
            description = "Heap usage where shedding starts (0.0-1.0)",
        ).default(0.9)
    private val memorySheddingFull by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.MEMORY_SHEDDING_FULL,
            description = "Heap usage where shedding is full (0.0-1.0)",
        ).default(0.98)
    private val loadSheddingRetryAfterSeconds by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.LOAD_SHEDDING_RETRY_AFTER,
            description = "Seconds to publish in the Retry-After header when shedding",
        ).default(30)
    private val requestDurationMinSamples by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES,
            description = "Minimum samples required before request duration shedding starts",
        ).default(200)
    private val requestDurationMinBaselineMs by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS,
            description = "Minimum baseline latency in milliseconds for request duration shedding",
        ).default(1.0)
    private val requestDurationRatioSlack by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK,
            description = "Ratio slack before request duration shedding ramps up",
        ).default(0.15)
    private val requestDurationFullSheddingRatio by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO,
            description = "Ratio at which request duration shedding is full",
        ).default(2.0)
    private val requestDurationSampleAlpha by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA,
            description = "EWMA alpha for request duration shedding samples",
        ).default(0.2)
    private val requestDurationDecayAlpha by
        option(
            ArgType.Double,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA,
            description = "EWMA alpha for request duration shedding decay",
        ).default(0.2)
    private val requestDurationSampleIntervalMs by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS,
            description = "Sampling interval in milliseconds for request duration shedding",
        ).default(250)
    private val podCertificateValiditySeconds by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.POD_CERT_VALIDITY_SECONDS,
            description = "Validity duration for pod certificates in seconds",
        ).default(7.days.inWholeSeconds.toInt())
    private val approveGidRateLimitPerMinute by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PER_MINUTE,
            description = "Allowed approvegid requests per minute per IP (positive value)",
        ).default(60)
    private val approveGidRateLimitBurst by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_BURST,
            description = "Burst size for approvegid rate limiting per IP (positive value)",
        ).default(20)
    private val authenticatedRateLimitPerMinute by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PER_MINUTE,
            description = "Allowed authenticated requests per minute per gid (positive value)",
        ).default(60)
    private val authenticatedRateLimitBurst by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_BURST,
            description = "Burst size for authenticated rate limiting per gid (positive value)",
        ).default(20)
    private val trustedProxyIps by
        option(
            ArgType.String,
            fullName = ServerCommandLineArguments.TRUSTED_PROXY_IPS,
            description = "Comma-separated IPs of trusted proxies for forwarded headers",
        ).default("")

    override fun execute() {
        val argumentValues =
            mapOf(
                ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
                ServerCommandLineArguments.SERVER_PORT to serverPort,
                ServerCommandLineArguments.ALLOW_HTTP to allowHttp,
                ServerCommandLineArguments.DEV_SELF_SIGNED_TLS to devSelfSignedTls,
                ServerCommandLineArguments.HTTPS_PORT to httpsPort,
                ServerCommandLineArguments.DEV_CERT_HOST to devCertHost,
                ServerCommandLineArguments.LOAD_SHEDDING_START to loadSheddingStart,
                ServerCommandLineArguments.LOAD_SHEDDING_FULL to loadSheddingFull,
                ServerCommandLineArguments.MEMORY_SHEDDING_START to memorySheddingStart,
                ServerCommandLineArguments.MEMORY_SHEDDING_FULL to memorySheddingFull,
                ServerCommandLineArguments.LOAD_SHEDDING_RETRY_AFTER to loadSheddingRetryAfterSeconds,
                ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES to requestDurationMinSamples,
                ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS to requestDurationMinBaselineMs,
                ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK to requestDurationRatioSlack,
                ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO to requestDurationFullSheddingRatio,
                ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA to requestDurationSampleAlpha,
                ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA to requestDurationDecayAlpha,
                ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS to requestDurationSampleIntervalMs,
                ServerCommandLineArguments.POD_CERT_VALIDITY_SECONDS to podCertificateValiditySeconds,
                ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PER_MINUTE to approveGidRateLimitPerMinute,
                ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_BURST to approveGidRateLimitBurst,
                ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PER_MINUTE to authenticatedRateLimitPerMinute,
                ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_BURST to authenticatedRateLimitBurst,
                ServerCommandLineArguments.TRUSTED_PROXY_IPS to trustedProxyIps,
            )
        logArguments(argumentValues)
        startServer(CommandLineArgumentStore.from { name -> argumentValues[name] })
    }
}

private fun startMosaServer(argumentStore: CommandLineArgumentStore) {
    val serverPort = argumentStore.getInt(ServerCommandLineArguments.SERVER_PORT) ?: 8080
    val allowHttp = argumentStore.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) ?: false
    val requireTlsForPod = !allowHttp
    val enableSelfSignedTls = argumentStore.getBoolean(ServerCommandLineArguments.DEV_SELF_SIGNED_TLS) ?: false
    val httpsPort = argumentStore.getInt(ServerCommandLineArguments.HTTPS_PORT) ?: 8443
    val devCertHost = argumentStore.getString(ServerCommandLineArguments.DEV_CERT_HOST) ?: "localhost"
    val loadSheddingStart = argumentStore.getDouble(ServerCommandLineArguments.LOAD_SHEDDING_START) ?: 0.85
    val loadSheddingFull = argumentStore.getDouble(ServerCommandLineArguments.LOAD_SHEDDING_FULL) ?: 0.95
    val memorySheddingStart = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_START) ?: 0.9
    val memorySheddingFull = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_FULL) ?: 0.98
    val loadSheddingRetryAfterSeconds = argumentStore.getInt(ServerCommandLineArguments.LOAD_SHEDDING_RETRY_AFTER) ?: 30
    val cpuSheddingConfig =
        CpuSheddingConfig(
            loadSheddingStart = loadSheddingStart,
            loadSheddingFull = loadSheddingFull,
        )
    val memorySheddingConfig =
        MemorySheddingConfig(
            memorySheddingStart = memorySheddingStart,
            memorySheddingFull = memorySheddingFull,
        )
    val mosaModule: Application.() -> Unit = {
        install(Koin) {
            modules(module { configureMosaAppModule(argumentStore) })
        }
        with(koin()) {
            installNetworkServerPlugins(
                requireTlsForPod,
                cpuSheddingConfig,
                memorySheddingConfig,
                loadSheddingRetryAfterSeconds.seconds,
                requestDurationForRouteStateRegistry = get(),
                meterRegistry = get(),
                nanoClock = get(),
                rng = get(),
                tokenUtil = get(),
            )
            routing {
                install(createMosaAuthenticatedRateLimitPlugin(get(authenticatedRateLimit)))
                getAll<BaseController>().forEach { register(it) }
            }
        }
    }
    val server =
        if (enableSelfSignedTls) {
            val keyStore = DevSelfSignedCertificateFactory().create(devCertHost)
            val connectors = devConnectors(allowHttp, serverPort, httpsPort, keyStore)
            CoroutineScope(Dispatchers.IO)
                .embeddedServer(
                    factory = Netty,
                    connectors = connectors.toTypedArray(),
                    watchPaths = emptyList(),
                    parentCoroutineContext = EmptyCoroutineContext,
                    module = mosaModule,
                )
        } else {
            embeddedServer(Netty, port = serverPort, module = mosaModule)
        }

    server.start(wait = true)
}

private fun devConnectors(
    allowHttp: Boolean,
    httpPort: Int,
    httpsPort: Int,
    keyStore: InMemoryKeyStore,
): List<EngineConnectorConfig> {
    val connectors = mutableListOf<EngineConnectorConfig>()
    if (allowHttp) {
        connectors.add(EngineConnectorBuilder().apply { port = httpPort })
    }
    connectors.add(
        EngineSSLConnectorBuilder(
            keyStore.keyStore,
            keyStore.alias,
            { keyStore.password },
            { keyStore.password },
        ).apply { port = httpsPort },
    )
    return connectors
}

private fun logCommandLineArguments(argumentValues: Map<String, Any>) {
    val formattedArguments =
        buildString {
            appendLine("Server command line options:")
            val maxKeyLength = argumentValues.keys.maxOfOrNull { it.length } ?: 0
            argumentValues
                .toSortedMap()
                .forEach { (key, value) ->
                    append("  ")
                    append(key.padEnd(maxKeyLength))
                    append(" = ")
                    appendLine(value.toString())
                }
        }
    log.info { formattedArguments }
}

private val log = KotlinLogging.logger {}
