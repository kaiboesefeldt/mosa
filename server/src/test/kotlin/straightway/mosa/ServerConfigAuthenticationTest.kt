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
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ServerConfigAuthenticationTest {
    @Test
    fun `authenticates valid jwt`() =
        withKoin { koin ->
            val tokenUtil = koin.get<TokenUtil>()
            val decodedJwt = mockk<DecodedJWT>()
            val certClaim = mockk<Claim>()

            every { tokenUtil.decode("valid") } returns decodedJwt
            every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
            every { decodedJwt.claims } returns mapOf("cert" to certClaim)
            every { certClaim.asMap() } returns mapOf("gid" to "user-123")

            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = true,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
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
    fun `rejects invalid jwt`() =
        withKoin { koin ->
            val tokenUtil = koin.get<TokenUtil>()
            val decodedJwt = mockk<DecodedJWT>(relaxed = true)

            every { tokenUtil.decode("invalid") } returns decodedJwt
            every { tokenUtil.isValidMisJwt(decodedJwt) } returns false

            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = true,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
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
    fun `rejects jwt without cert claim`() =
        withKoin { koin ->
            val tokenUtil = koin.get<TokenUtil>()
            val decodedJwt = mockk<DecodedJWT>()
            val otherClaim = mockk<Claim>()

            every { tokenUtil.decode("nocert") } returns decodedJwt
            every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
            every { decodedJwt.claims } returns mapOf("other" to otherClaim)

            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = true,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
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
    fun `rejects jwt with empty certificate`() =
        withKoin { koin ->
            val tokenUtil = koin.get<TokenUtil>()
            val decodedJwt = mockk<DecodedJWT>()
            val certClaim = mockk<Claim>()

            every { tokenUtil.decode("nogid") } returns decodedJwt
            every { tokenUtil.isValidMisJwt(decodedJwt) } returns true
            every { decodedJwt.claims } returns mapOf("cert" to certClaim)
            every { certClaim.asMap() } returns emptyMap()

            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = true,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
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
    fun `handles failures from token util`() =
        withKoin { koin ->
            val tokenUtil = koin.get<TokenUtil>()
            val anonymousFailure =
                object : RuntimeException("decode failure") {}

            every { tokenUtil.decode("boom") } throws anonymousFailure

            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = true,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
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

    private fun withKoin(block: (Koin) -> Unit) =
        koinApplication {
            modules(
                module {
                    single<TokenUtil> { mockk<TokenUtil>() }
                    single<MeterRegistry> { mockk<MeterRegistry>(relaxed = true) }
                },
            )
        }.let { block(it.koin) }
}
