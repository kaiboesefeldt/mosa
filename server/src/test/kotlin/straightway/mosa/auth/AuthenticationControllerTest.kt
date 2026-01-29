package straightway.mosa.auth

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import straightway.mosa.NanoClock
import straightway.mosa.base.register
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.network.ClientIpResolver
import straightway.mosa.testutils.setJsonBody
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class AuthenticationControllerTest {
    private val clock =
        Clock.fixed(
            Instant.ofEpochSecond(170000000L),
            ZoneId.of("UTC"),
        )
    private val nanoClock = NanoClock { 0L }
    private val rateLimiter = RateLimiter(nanoClock, RateLimitConfig(permitsPerMinute = 1, burst = 10_000))
    private val clientIpResolver = ClientIpResolver(emptySet())
    private val signatureFactory = TransientEd25519Factory()
    private val podCertificateService = PodCertificateService(signatureFactory, clock)
    private val environment =
        ControllerHttpTestEnvironment(
            AuthenticationController(
                podCertificateService,
                clock,
                certificateLifeTime,
                rateLimiter,
                clientIpResolver,
                identityBlockListFromYaml(" "),
            ),
        ) {
            single { podCertificateService }
            single { clock }
        }

    @Test
    fun `approvegid rejects unauthenticated http request when tls required`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.XForwardedProto, "http")
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `approvegid accepts unauthenticated http request when tls allowed`() {
        val httpEnvironment =
            ControllerHttpTestEnvironment(
                AuthenticationController(
                    podCertificateService,
                    clock,
                    certificateLifeTime,
                    rateLimiter,
                    clientIpResolver,
                    identityBlockListFromYaml(" "),
                ),
                requireTlsForPod = false,
            ) {
                single { podCertificateService }
                single { clock }
            }

        httpEnvironment {
            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.XForwardedProto, "http")
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
    }

    @Test
    fun `approvegid returns bad request when gid is not base64url encoded`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "not base64",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `approvegid returns refresh recommendation timestamp`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val certificate = response.body<PodCertificateResponse>()
            assertEquals(expectedRefreshRecommendation, certificate.refreshRecommendedAt)
        }

    @Test
    fun `approvegid bad request includes cors headers`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "not base64",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }

    @Test
    fun `approvegid returns too many requests when rate limit exceeded`() {
        val strictRateLimiter = RateLimiter(nanoClock, RateLimitConfig(permitsPerMinute = 1, burst = 1))
        val strictEnvironment =
            ControllerHttpTestEnvironment(
                AuthenticationController(
                    podCertificateService,
                    clock,
                    certificateLifeTime,
                    strictRateLimiter,
                    clientIpResolver,
                    identityBlockListFromYaml(" "),
                ),
            ) {
                single { podCertificateService }
                single { clock }
            }

        strictEnvironment {
            val firstResponse =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, firstResponse.status)

            val response =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.TooManyRequests, response.status)
            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toInt()
            assertTrue(retryAfter in 60..61)
            assertEquals(mapOf("message" to "Rate limit exceeded"), response.body())
        }
    }

    @Test
    fun `refreshgid rejects unauthenticated request`() =
        environment {
            val response = client.post("/v1/refreshgid")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `refreshgid responds unauthorized when principal lacks certificate`() =
        testApplication {
            application {
                install(Authentication) {
                    bearer {
                        authenticate { object {} }
                    }
                }
                install(ServerContentNegotiation) {
                    jackson()
                }
                routing {
                    register(
                        AuthenticationController(
                            podCertificateService,
                            clock,
                            certificateLifeTime,
                            rateLimiter,
                            clientIpResolver,
                            identityBlockListFromYaml(" "),
                        ),
                    )
                }
            }

            client =
                createClient {
                    install(ClientContentNegotiation) {
                        jackson()
                    }
                }

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer token")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(mapOf("message" to "Missing authenticated certificate for /v1/refreshgid"), response.body())
        }

    @Test
    fun `refreshgid responds unauthorized when certificate access fails without message`() =
        testApplication {
            val principal = mockk<PodPrincipal>()
            every { principal.certificate } throws RuntimeException()

            application {
                install(Authentication) {
                    bearer {
                        authenticate { principal }
                    }
                }
                install(ServerContentNegotiation) {
                    jackson()
                }
                routing {
                    register(
                        AuthenticationController(
                            podCertificateService,
                            clock,
                            certificateLifeTime,
                            rateLimiter,
                            clientIpResolver,
                            identityBlockListFromYaml(" "),
                        ),
                    )
                }
            }

            client =
                createClient {
                    install(ClientContentNegotiation) {
                        jackson()
                    }
                }

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer token")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(mapOf("message" to "Missing authenticated certificate"), response.body())
        }

    @Test
    fun `refreshgid returns certificate with same inf`() =
        environment {
            val inf = "info".toBase64Url()
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = 600L, inf = inf)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val refreshed = response.body<PodCertificateResponse>()
            assertEquals(environment.gid, refreshed.certificate.gid)
            assertEquals(inf, refreshed.certificate.inf)
        }

    @Test
    fun `refreshgid returns refresh recommendation timestamp`() =
        environment {
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = refreshThresholdSeconds - 1)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val refreshed = response.body<PodCertificateResponse>()
            assertEquals(expectedRefreshRecommendation, refreshed.refreshRecommendedAt)
        }

    @Test
    fun `refreshgid rejects certificate that is too fresh`() =
        environment {
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = certificateLifeTime.inWholeSeconds)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(mapOf("message" to "Certificate is too fresh to refresh"), response.body())
        }

    @Test
    fun `approvegid rejects blocked gid`() {
        val blockedGid = "blocked".toBase64Url()
        val blockedEnvironment =
            ControllerHttpTestEnvironment(
                AuthenticationController(
                    podCertificateService,
                    clock,
                    certificateLifeTime,
                    rateLimiter,
                    clientIpResolver,
                    identityBlockListFromYaml(
                        """
                        blockedGids:
                          - "$blockedGid"
                        """.trimIndent(),
                    ),
                ),
            ) {
                single { podCertificateService }
                single { clock }
            }

        blockedEnvironment {
            val response =
                client.post("/v1/approvegid") {
                    setJsonBody(
                        AuthenticationApprovalRequest(
                            gid = blockedGid,
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(mapOf("message" to "Identity blocked"), response.body())
        }
    }

    private fun String.toBase64Url(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodeToByteArray())

    private fun identityBlockListFromYaml(yaml: String): AutoRefreshingFileBackedContent<IdentityBlockList> {
        val path = createTempFile("mosa_identity_blocklist_test", ".yaml")
        path.writeText(yaml)
        return AutoRefreshingFileBackedContent(
            AutoRefreshingFileBackedContent.Config(
                path = path,
                refreshInterval = 5.seconds,
            ),
            Clock.systemUTC(),
        ) { content ->
            IdentityBlockList.transformer.transform(content)
        }
    }

    private companion object {
        private val certificateLifeTime: Duration = 7.days
        private val refreshThresholdSeconds = certificateLifeTime.inWholeSeconds / 10
        private val expectedRefreshRecommendation = 170000000L + certificateLifeTime.inWholeSeconds - (refreshThresholdSeconds / 2)
    }
}
