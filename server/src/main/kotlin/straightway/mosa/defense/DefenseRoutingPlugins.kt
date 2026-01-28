package straightway.mosa.defense

import io.ktor.server.routing.Routing
import org.koin.ktor.ext.getKoin
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.defense.blocklist.createMosaAuthenticatedIdentityBlockListPlugin
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.ratelimit.createMosaAuthenticatedRateLimitPlugin

fun Routing.installDefenseRoutingPlugins() {
    val koin = getKoin()
    val identityBlockList = koin.get<AutoRefreshingFileBackedContent<IdentityBlockList>>(identityBlockList)
    val authenticatedRateLimiter = koin.get<RateLimiter>(authenticatedRateLimit)
    install(createMosaAuthenticatedIdentityBlockListPlugin(identityBlockList))
    install(createMosaAuthenticatedRateLimitPlugin(authenticatedRateLimiter))
}
