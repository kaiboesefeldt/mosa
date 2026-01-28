package straightway.mosa.commandline

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.AuthConfig
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.LoadSheddingConfig
import straightway.mosa.ServerCommandLineArguments
import straightway.mosa.TlsConfig
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.configureMosaAppModule
import straightway.mosa.createMosaAuthenticatedIdentityBlockListPlugin
import straightway.mosa.createMosaAuthenticatedRateLimitPlugin
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.defense.authenticatedRateLimit
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.identityBlockList
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.defaultMaxRequestBodyBytes
import straightway.mosa.doc.ServeDocRenderer
import straightway.mosa.installNetworkServerPlugins
import straightway.mosa.tls.DevSelfSignedCertificateFactory
import straightway.mosa.tls.InMemoryKeyStore
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCli::class)
internal class ServeCommand(
    private val startServer: (CommandLineArgumentStore) -> Unit = ::startMosaServer,
    private val logArguments: (Map<String, Any?>) -> Unit = ::logCommandLineArguments,
) : Subcommand(
        "serve",
        "Start the MOSA server",
    ) {
    private val serveSourceMaps by
        option(
            ArgType.Boolean,
            fullName = ServerCommandLineArguments.SERVE_SOURCE_MAPS,
            description = "Serve source maps for the client bundle",
        ).default(false)
    private val doc by
        option(
            ArgType.Boolean,
            shortName = "d",
            fullName = "doc",
            description = "Print detailed serve documentation and exit",
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
    private val trustedProxyIps by
        option(
            ArgType.String,
            fullName = ServerCommandLineArguments.TRUSTED_PROXY_IPS,
            description = "Comma-separated IPs of trusted proxies for forwarded headers",
        ).default("")
    private val requestReadTimeoutSeconds by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.REQUEST_READ_TIMEOUT_SECONDS,
            description = "Request read timeout in seconds",
        ).default(20)
    private val responseWriteTimeoutSeconds by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.RESPONSE_WRITE_TIMEOUT_SECONDS,
            description = "Response write timeout in seconds",
        ).default(30)
    private val maxFetchItems by
        option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.MAX_FETCH_ITEMS,
            description = "Maximum number of items allowed per fetch request",
        ).default(100)
    private val defenseOptions = DefenseCommandLineOptions(this)

    override fun execute() {
        if (doc) {
            ServeDocRenderer().render(readServeDocumentation())
            return
        }
        val argumentValues =
            mapOf(
                ServerCommandLineArguments.SERVE_SOURCE_MAPS to serveSourceMaps,
                ServerCommandLineArguments.SERVER_PORT to serverPort,
                ServerCommandLineArguments.ALLOW_HTTP to allowHttp,
                ServerCommandLineArguments.DEV_SELF_SIGNED_TLS to devSelfSignedTls,
                ServerCommandLineArguments.HTTPS_PORT to httpsPort,
                ServerCommandLineArguments.DEV_CERT_HOST to devCertHost,
                ServerCommandLineArguments.TRUSTED_PROXY_IPS to trustedProxyIps,
                ServerCommandLineArguments.REQUEST_READ_TIMEOUT_SECONDS to requestReadTimeoutSeconds,
                ServerCommandLineArguments.RESPONSE_WRITE_TIMEOUT_SECONDS to responseWriteTimeoutSeconds,
                ServerCommandLineArguments.MAX_FETCH_ITEMS to maxFetchItems,
            ) + defenseOptions.toArgumentValues()
        logArguments(argumentValues)
        startServer(CommandLineArgumentStore.from { name -> argumentValues[name] })
    }
}

private fun readServeDocumentation(): String = readMarkdownResource("/doc/serve/index.md")

private fun readMarkdownResource(path: String): String {
    val content =
        ServeCommand::class.java
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return "Missing serve documentation resource: ${path.removePrefix("/")}"
    return expandMarkdownIncludes(content)
}

private fun expandMarkdownIncludes(markdown: String): String {
    val includePattern = Regex("\\{\\{include:([^}]+)\\}\\}")
    return buildString {
        markdown.lineSequence().forEach { line ->
            val match = includePattern.matchEntire(line.trim())
            if (match == null) {
                appendLine(line)
            } else {
                val includePath = match.groupValues[1].trim()
                appendLine(readMarkdownResource(includePath))
            }
        }
    }.trimEnd()
}

private fun startMosaServer(argumentStore: CommandLineArgumentStore) {
    val serverPort = argumentStore.getInt(ServerCommandLineArguments.SERVER_PORT) ?: 8080
    val allowHttp = argumentStore.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) ?: false
    val requireTlsForPod = !allowHttp
    val enableSelfSignedTls = argumentStore.getBoolean(ServerCommandLineArguments.DEV_SELF_SIGNED_TLS) ?: false
    val httpsPort = argumentStore.getInt(ServerCommandLineArguments.HTTPS_PORT) ?: 8443
    val devCertHost = argumentStore.getString(ServerCommandLineArguments.DEV_CERT_HOST) ?: "localhost"
    val loadSheddingStart = argumentStore.getDouble(ServerCommandLineArguments.CPU_LOAD_SHEDDING_START) ?: 0.85
    val loadSheddingFull = argumentStore.getDouble(ServerCommandLineArguments.CPU_LOAD_SHEDDING_FULL) ?: 0.95
    val cpuLoadSmoothingHalfLifeSeconds =
        argumentStore.getDouble(ServerCommandLineArguments.CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS) ?: 10.0
    val cpuLoadSmoothingHalfLifeNs =
        (cpuLoadSmoothingHalfLifeSeconds * 1_000_000_000.0).roundToLong()
    val heapMemorySheddingStart = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_START) ?: 0.9
    val heapMemorySheddingFull = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_FULL) ?: 0.98
    val nonHeapMemorySheddingStart = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_START) ?: 0.98
    val nonHeapMemorySheddingFull = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_FULL) ?: 0.999
    val systemMemorySheddingStart = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_START) ?: 0.85
    val systemMemorySheddingFull = argumentStore.getDouble(ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_FULL) ?: 0.93
    val loadSheddingRetryAfterSeconds = argumentStore.getInt(ServerCommandLineArguments.CPU_LOAD_SHEDDING_RETRY_AFTER) ?: 30
    val maxFetchItems = argumentStore.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS) ?: 100
    val maxRequestBodyBytes =
        argumentStore.getInt(ServerCommandLineArguments.MAX_REQUEST_BODY_BYTES)?.toLong()
            ?: defaultMaxRequestBodyBytes(maxFetchItems).also {
                log.info { "Actual Max Request Body Bytes: $it" }
            }
    val requestReadTimeoutSeconds =
        argumentStore.getInt(ServerCommandLineArguments.REQUEST_READ_TIMEOUT_SECONDS) ?: 20
    val responseWriteTimeoutSeconds =
        argumentStore.getInt(ServerCommandLineArguments.RESPONSE_WRITE_TIMEOUT_SECONDS) ?: 30
    val cpuSheddingConfig =
        CpuSheddingConfig(
            loadSheddingStart = loadSheddingStart,
            loadSheddingFull = loadSheddingFull,
        )
    val heapMemorySheddingConfig =
        MemorySheddingConfig(
            memorySheddingStart = heapMemorySheddingStart,
            memorySheddingFull = heapMemorySheddingFull,
        )
    val nonHeapMemorySheddingConfig =
        MemorySheddingConfig(
            memorySheddingStart = nonHeapMemorySheddingStart,
            memorySheddingFull = nonHeapMemorySheddingFull,
        )
    val systemMemorySheddingConfig =
        MemorySheddingConfig(
            memorySheddingStart = systemMemorySheddingStart,
            memorySheddingFull = systemMemorySheddingFull,
        )
    val mosaModule: Application.() -> Unit = {
        install(Koin) {
            modules(
                module {
                    configureMosaAppModule(argumentStore)
                    single { TlsConfig(requireTlsForPod = requireTlsForPod) }
                    single {
                        LoadSheddingConfig(
                            cpuSheddingConfig = cpuSheddingConfig,
                            heapMemorySheddingConfig = heapMemorySheddingConfig,
                            nonHeapMemorySheddingConfig = nonHeapMemorySheddingConfig,
                            systemMemorySheddingConfig = systemMemorySheddingConfig,
                            cpuLoadSmoothingHalfLifeNs = cpuLoadSmoothingHalfLifeNs,
                            loadSheddingRetryAfter = loadSheddingRetryAfterSeconds.seconds,
                        )
                    }
                    single {
                        AuthConfig(
                            tokenUtil = get(),
                            clientIpResolver = get<ClientIpResolver>(),
                        )
                    }
                    single {
                        RequestBodyLimitConfig(
                            maxBodyBytes = maxRequestBodyBytes,
                        )
                    }
                },
            )
        }
        with(koin()) {
            installNetworkServerPlugins()
            routing {
                install(createMosaAuthenticatedIdentityBlockListPlugin(get(identityBlockList)))
                install(createMosaAuthenticatedRateLimitPlugin(get(authenticatedRateLimit)))
                getAll<BaseController>().forEach { register(it) }
            }
        }
    }
    val server =
        if (enableSelfSignedTls) {
            val keyStore = DevSelfSignedCertificateFactory().create(devCertHost)
            val connectors = devConnectors(allowHttp, serverPort, httpsPort, keyStore)
            val rootConfig =
                serverConfig(applicationEnvironment()) {
                    parentCoroutineContext = Dispatchers.IO
                    watchPaths = emptyList()
                    module(mosaModule)
                }
            embeddedServer(Netty, rootConfig) {
                this.connectors.addAll(connectors)
                this.requestReadTimeoutSeconds = requestReadTimeoutSeconds
                this.responseWriteTimeoutSeconds = responseWriteTimeoutSeconds
            }
        } else {
            val rootConfig =
                serverConfig(applicationEnvironment()) {
                    module(mosaModule)
                }
            embeddedServer(Netty, rootConfig) {
                this.connectors.add(
                    EngineConnectorBuilder().apply { port = serverPort },
                )
                this.requestReadTimeoutSeconds = requestReadTimeoutSeconds
                this.responseWriteTimeoutSeconds = responseWriteTimeoutSeconds
            }
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

private fun logCommandLineArguments(argumentValues: Map<String, Any?>) {
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
