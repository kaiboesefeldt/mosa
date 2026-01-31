package straightway.mosa.network

import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.dev.DevCommandLineOptions
import straightway.mosa.dev.DevServer

fun createServer(
    argumentStore: CommandLineArgumentStore,
    module: Application.() -> Unit,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val enableSelfSignedTls = argumentStore.getBoolean(DevCommandLineOptions.DEV_SELF_SIGNED_TLS) ?: false
    return if (enableSelfSignedTls) {
        DevServer.createServerWithSelfSignedTls(argumentStore, module)
    } else {
        createServerWithoutSelfSignedTls(argumentStore, module)
    }
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
