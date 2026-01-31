package straightway.mosa.network

import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.engine.EngineSSLConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.Dispatchers
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.ServerCommandLineOptions
import straightway.mosa.tls.DevSelfSignedCertificateFactory
import straightway.mosa.tls.InMemoryKeyStore

fun createServer(
    argumentStore: CommandLineArgumentStore,
    module: Application.() -> Unit,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val enableSelfSignedTls = argumentStore.getBoolean(ServerCommandLineOptions.DEV_SELF_SIGNED_TLS) ?: false
    return if (enableSelfSignedTls) {
        createServerWithSelfSignedTls(argumentStore, module)
    } else {
        createServerWithoutSelfSignedTls(argumentStore, module)
    }
}

private fun createServerWithSelfSignedTls(
    argumentStore: CommandLineArgumentStore,
    module: Application.() -> Unit,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val allowHttp = argumentStore.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP) ?: false
    val serverPort = argumentStore.getInt(NetworkCommandLineOptions.SERVER_PORT) ?: 8080
    val httpsPort = argumentStore.getInt(NetworkCommandLineOptions.HTTPS_PORT) ?: 80443
    val devCertHost = argumentStore.getString(ServerCommandLineOptions.DEV_CERT_HOST) ?: "localhost"
    val keyStore = DevSelfSignedCertificateFactory().create(devCertHost)
    val connectors = devConnectors(allowHttp, serverPort, httpsPort, keyStore)
    val rootConfig =
        serverConfig(applicationEnvironment()) {
            parentCoroutineContext = Dispatchers.IO
            watchPaths = emptyList()
            module(module)
        }
    val requestReadTimeoutSeconds = argumentStore.getInt(NetworkCommandLineOptions.REQUEST_READ_TIMEOUT_SECONDS) ?: 20
    val responseWriteTimeoutSeconds = argumentStore.getInt(NetworkCommandLineOptions.RESPONSE_WRITE_TIMEOUT_SECONDS) ?: 30
    return embeddedServer(Netty, rootConfig) {
        this.connectors.addAll(connectors)
        this.requestReadTimeoutSeconds = requestReadTimeoutSeconds
        this.responseWriteTimeoutSeconds = responseWriteTimeoutSeconds
    }
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

private fun createServerWithoutSelfSignedTls(
    argumentStore: CommandLineArgumentStore,
    module: Application.() -> Unit,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val rootConfig =
        serverConfig(applicationEnvironment()) {
            module(module)
        }
    val serverPort = argumentStore.getInt(NetworkCommandLineOptions.SERVER_PORT) ?: 8080
    val requestReadTimeoutSeconds = argumentStore.getInt(NetworkCommandLineOptions.REQUEST_READ_TIMEOUT_SECONDS) ?: 20
    val responseWriteTimeoutSeconds = argumentStore.getInt(NetworkCommandLineOptions.RESPONSE_WRITE_TIMEOUT_SECONDS) ?: 30
    return embeddedServer(Netty, rootConfig) {
        this.connectors.add(
            EngineConnectorBuilder().apply { port = serverPort },
        )
        this.requestReadTimeoutSeconds = requestReadTimeoutSeconds
        this.responseWriteTimeoutSeconds = responseWriteTimeoutSeconds
    }
}
