package straightway.mosa

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.content.BlockedIdentityStore
import straightway.mosa.defense.blocklist.createMosaAuthenticatedIdentityBlockListPlugin
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.ratelimit.createMosaAuthenticatedRateLimitPlugin
import straightway.mosa.network.ClientIpResolver
import straightway.mosa.testutils.installTestNetworkServerPlugins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ServerConfigAuthenticationTest {
    private val trustedProxyResolver = ClientIpResolver(setOf("localhost"))

    @Test
    fun `authenticates valid jwt`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()
        val pubClaim = mockk<Claim>()

        every { tokenUtil.decode("valid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim, "pub" to pubClaim)
        every { certClaim.asMap() } returns mapOf("gid" to "user-123", "exp" to 1, "sig" to "sig")
        every { pubClaim.asString() } returns "public-key"

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    authenticate {
                        get("/secure") {
                            val principal = call.principal<PodPrincipal>()
                            call.respondText(principal?.gid ?: "none")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer valid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("user-123", response.bodyAsText())
        }
    }

    @Test
    fun `rate limits authenticated requests`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()
        val pubClaim = mockk<Claim>()
        val rateLimiter = RateLimiter({ 0L }, RateLimitConfig(permitsPerMinute = 60, burst = 1))

        every { tokenUtil.decode("valid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim, "pub" to pubClaim)
        every { certClaim.asMap() } returns mapOf("gid" to "user-123", "exp" to 1, "sig" to "sig")
        every { pubClaim.asString() } returns "public-key"

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    install(createMosaAuthenticatedRateLimitPlugin(rateLimiter))
                    authenticate {
                        get("/secure") {
                            call.respondText("ok")
                        }
                    }
                }
            }

            val first =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer valid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            val second =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer valid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.TooManyRequests, second.status)
            val retryAfter = second.headers[HttpHeaders.RetryAfter]?.toInt()
            assertTrue(retryAfter in 1..2)
        }
    }

    @Test
    fun `rejects authenticated request when gid is blocked`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()
        val pubClaim = mockk<Claim>()
        val blockedGid = "blocked-user"

        every { tokenUtil.decode("valid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim, "pub" to pubClaim)
        every { certClaim.asMap() } returns mapOf("gid" to blockedGid, "exp" to 1, "sig" to "sig")
        every { pubClaim.asString() } returns "public-key"

        val blockedIdentityStore = InMemoryBlockedIdentityStore().apply { block(blockedGid) }

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    install(createMosaAuthenticatedIdentityBlockListPlugin(blockedIdentityStore))
                    authenticate {
                        get("/secure") {
                            call.respondText("ok")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer valid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("""{"message":"Identity blocked"}""", response.bodyAsText())
        }
    }

    @Test
    fun `allows blocked gid to delete own identity`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()
        val pubClaim = mockk<Claim>()
        val blockedGid = "blocked-user"

        every { tokenUtil.decode("valid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim, "pub" to pubClaim)
        every { certClaim.asMap() } returns mapOf("gid" to blockedGid, "exp" to 1, "sig" to "sig")
        every { pubClaim.asString() } returns "public-key"

        val blockedIdentityStore = InMemoryBlockedIdentityStore().apply { block(blockedGid) }

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    install(createMosaAuthenticatedIdentityBlockListPlugin(blockedIdentityStore))
                    authenticate {
                        delete("/v1/identity") {
                            call.respondText("deleted")
                        }
                    }
                }
            }

            val response =
                client.delete("/v1/identity") {
                    header(HttpHeaders.Authorization, "Bearer valid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("deleted", response.bodyAsText())
        }
    }

    @Test
    fun `rejects invalid jwt`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>(relaxed = true)

        every { tokenUtil.decode("invalid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns false

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    authenticate {
                        get("/secure") {
                            call.respondText("should-not-run")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer invalid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `rejects jwt without cert claim`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val otherClaim = mockk<Claim>()

        every { tokenUtil.decode("nocert") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("other" to otherClaim)

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    authenticate {
                        get("/secure") {
                            call.respondText("should-not-run")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer nocert")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `rejects jwt with empty certificate`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()

        every { tokenUtil.decode("nogid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim)
        every { certClaim.asMap() } returns emptyMap()

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    authenticate {
                        get("/secure") {
                            call.respondText("should-not-run")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer nogid")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `handles failures from token util`() {
        val tokenUtil = mockk<TokenUtil>()
        val anonymousFailure =
            object : RuntimeException("decode failure") {}

        every { tokenUtil.decode("boom") } throws anonymousFailure

        testApplication {
            application {
                installTestNetworkServerPlugins(tokenUtil = tokenUtil, clientIpResolver = trustedProxyResolver)
                routing {
                    authenticate {
                        get("/secure") {
                            call.respondText("should-not-run")
                        }
                    }
                }
            }

            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer boom")
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}

private class InMemoryBlockedIdentityStore : BlockedIdentityStore {
    private val blockedGids = linkedSetOf<String>()

    override fun isBlocked(gid: String): Boolean = gid in blockedGids

    override fun block(gid: String): Boolean = blockedGids.add(gid)

    override fun unblock(gid: String): Boolean = blockedGids.remove(gid)

    override fun delete(gid: String): Boolean = blockedGids.remove(gid)
}
