package straightway.mosa.defense

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ktor.plugin.koin
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.defense.blocklist.createMosaIpBlockListPlugin
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.createMosaLoadSheddingPlugin
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.ratelimit.createMosaIpRateLimitPlugin
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.createMosaRequestBodyLimitPlugin
import straightway.mosa.network.ClientIpResolver

fun Application.installDefenseServerPlugins() {
    val koin = koin()
    val clientIpResolver = koin.get<ClientIpResolver>()
    val requestBodyLimitConfig = koin.get<RequestBodyLimitConfig>()
    val ipBlockList = koin.get<AutoRefreshingFileBackedContent<ClientBlockList>>(ipBlockList)
    val ipRateLimiter = koin.get<RateLimiter>(ipRateLimit)

    install(RequestMetricsPlugin())
    install(createMosaLoadSheddingPlugin())
    install(createMosaIpBlockListPlugin(clientIpResolver, ipBlockList))
    install(createMosaIpRateLimitPlugin(ipRateLimiter, clientIpResolver))
    install(createMosaRequestBodyLimitPlugin(requestBodyLimitConfig))
}
