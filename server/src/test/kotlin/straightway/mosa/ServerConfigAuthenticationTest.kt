package straightway.mosa

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConfigAuthenticationTest {
    @Test
    fun `authenticates valid jwt`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>()
        val certClaim = mockk<Claim>()

        every { tokenUtil.decode("valid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
        every { decodedJwt.claims } returns mapOf("cert" to certClaim)
        every { certClaim.asMap() } returns mapOf("gid" to "user-123")

        testApplication {
            application {
                installNetworkServerPlugins(tokenUtil, requireTlsForPod = true)
                routing {
                    authenticate {
                        get("/secure") {
                            val principal = call.principal<UserIdPrincipal>()
                            call.respondText(principal?.name ?: "none")
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
    fun `rejects invalid jwt`() {
        val tokenUtil = mockk<TokenUtil>()
        val decodedJwt = mockk<DecodedJWT>(relaxed = true)

        every { tokenUtil.decode("invalid") } returns decodedJwt
        every { tokenUtil.isValidMisJwt(decodedJwt) } returns false

        testApplication {
            application {
                installNetworkServerPlugins(tokenUtil, requireTlsForPod = true)
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
                installNetworkServerPlugins(tokenUtil, requireTlsForPod = true)
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
                installNetworkServerPlugins(tokenUtil, requireTlsForPod = true)
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
                installNetworkServerPlugins(tokenUtil, requireTlsForPod = true)
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
