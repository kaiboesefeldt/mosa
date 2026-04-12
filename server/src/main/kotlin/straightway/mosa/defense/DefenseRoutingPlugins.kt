package straightway.mosa.defense

import io.ktor.server.routing.Routing
import org.koin.ktor.ext.getKoin
import straightway.mosa.content.BlockedIdentityStore
import straightway.mosa.defense.blocklist.createMosaAuthenticatedIdentityBlockListPlugin
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.ratelimit.createMosaAuthenticatedRateLimitPlugin

fun Routing.installDefenseRoutingPlugins() {
    val koin = getKoin()
    val blockedIdentityStore = koin.get<BlockedIdentityStore>()
    val authenticatedRateLimiter = koin.get<RateLimiter>(authenticatedRateLimit)
    install(createMosaAuthenticatedIdentityBlockListPlugin(blockedIdentityStore))
    install(createMosaAuthenticatedRateLimitPlugin(authenticatedRateLimiter))
}
