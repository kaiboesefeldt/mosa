package straightway.mosa.defense.blocklist

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.config.contains
import straightway.mosa.defense.core.clientIpAddress
import straightway.mosa.network.ClientIpResolver
import java.net.InetAddress

fun createMosaIpBlockListPlugin(
    clientIpResolver: ClientIpResolver,
    ipBlockList: AutoRefreshingFileBackedContent<ClientBlockList>,
): Plugin<Application, Unit, Unit> =
    object : Plugin<Application, Unit, Unit> {
        override val key = AttributeKey<Unit>("MosaIpBlockList")

        override fun install(
            pipeline: Application,
            configure: Unit.() -> Unit,
        ) {
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val blockList = ipBlockList.get() ?: return@intercept
                val address =
                    runCatching { InetAddress.getByName(call.clientIpAddress(clientIpResolver)) }.getOrNull()
                        ?: return@intercept
                if (address !in blockList) return@intercept
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to CLIENT_BLOCKED_MESSAGE))
                finish()
            }
        }
    }

private const val CLIENT_BLOCKED_MESSAGE = "Client blocked"
