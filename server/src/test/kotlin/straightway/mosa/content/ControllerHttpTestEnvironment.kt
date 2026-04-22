package straightway.mosa.content

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.apache.lucene.store.ByteBuffersDirectory
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import straightway.mosa.Crypto
import straightway.mosa.EmbeddingService
import straightway.mosa.HasherFactory
import straightway.mosa.NanoClock
import straightway.mosa.SignatureFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.TokenUtil
import straightway.mosa.VectorStore
import straightway.mosa.auth.Auth0TokenUtil
import straightway.mosa.auth.JwtBuilder
import straightway.mosa.auth.PodCertificateService
import straightway.mosa.auth.ThreadLocalEd25519VerifierProvider
import straightway.mosa.auth.TransientEd25519Factory
import straightway.mosa.base.BaseController
import straightway.mosa.base.register
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.ratelimit.createMosaAuthenticatedRateLimitPlugin
import straightway.mosa.jackson.InstantJacksonSerializers
import straightway.mosa.network.ClientIpResolver
import straightway.mosa.testutils.installTestNetworkServerPlugins
import straightway.mosa.vectorstore.LuceneIndexAccess
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.vectorstore.LuceneQueryPlanner
import straightway.mosa.vectorstore.QueryOnRegistry
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

class ControllerHttpTestEnvironment(
    val controllers: List<BaseController>,
    private val requireTlsForPod: Boolean = true,
    val additionalTestModules: org.koin.core.module.Module.() -> Unit = {},
) {
    val crypto = CryptoEd25519()
    val clock: Clock =
        Clock.fixed(
            Instant.ofEpochSecond(170000000L),
            ZoneId.of("UTC"),
        )
    val clientKeyPair = crypto.generateKeyPair()
    val signatureFactory = TransientEd25519Factory()
    val signatureVerifierProvider = ThreadLocalEd25519VerifierProvider()
    val podCertService = PodCertificateService(signatureFactory)
    val hasherFactory = { MessageDigest.getInstance("SHA-256") }
    val authenticatedRateLimiter =
        RateLimiter({ 0L }, RateLimitConfig(permitsPerMinute = 60, burst = 1_000_000))
    val gid =
        Base64.UrlSafe
            .withPadding(Base64.PaddingOption.ABSENT)
            .encode(
                hasherFactory().digest(
                    clientKeyPair.public.encoded
                        .takeLast(32)
                        .toByteArray(),
                ),
            )
    val podCertificate = podCertService.approveGid(gid, validitySeconds = 3600L)
    val jwtBuilder = JwtBuilder(clock, 30.days)
    val bearerToken = jwtBuilder.buildMisJwt(clientKeyPair, podCertificate)

    constructor(
        controller: BaseController,
        requireTlsForPod: Boolean = true,
        additionalTestModules: org.koin.core.module.Module.() -> Unit = {},
    ) : this(listOf(controller), requireTlsForPod, additionalTestModules)

    operator fun invoke(
        vararg controllers: BaseController,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        val testModule =
            module {
                single<LuceneIndexAccess> { LuceneIndexAccess { ByteBuffersDirectory() } } onClose { (it as? AutoCloseable)?.close() }
                single<VectorStore> {
                    LuceneLocalVectorStore.create(
                        indexAccess = get(),
                        paginationRegistry = QueryOnRegistry(pageSize = 10, maxNotFinishedQueries = 10000),
                        queryPlanner = LuceneQueryPlanner(maxResults = 1000),
                        clock = clock,
                        vectorSize = get<EmbeddingService>().dimension,
                    )
                } onClose { (it as? AutoCloseable)?.close() }
                single<HasherFactory> { hasherFactory }
                single<Clock> { clock }
                single<NanoClock> { NanoClock { 1L } }
                single<Random> { Random(12345L) }
                single<SignatureFactory> { signatureFactory }
                single<SignatureVerifierProvider> { signatureVerifierProvider }
                single<Crypto> { crypto }
                single<TokenUtil> { Auth0TokenUtil(get(), get(), get(), get(), get()) }
                single<ContentService> {
                    ContentService(
                        get(),
                        get(),
                        get(),
                        maxFetchItems = 100,
                        postComplaintStore = mockk(relaxed = true),
                    )
                }
                single<PodCertificateService> { podCertService }
                single<MeterRegistry> { SimpleMeterRegistry() }
                additionalTestModules()
            }
        testApplication {
            install(Koin) {
                allowOverride(true)
                modules(
                    module {
                        single<TokenUtil> { mockk<TokenUtil>() }
                        single<NanoClock> { NanoClock { 1L } }
                        single<MeterRegistry> { mockk<MeterRegistry>(relaxed = true) }
                        single<Random> { Random(12345L) }
                    },
                    testModule,
                )
            }
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = requireTlsForPod,
                    tokenUtil = getKoin().get(),
                    clientIpResolver = ClientIpResolver(setOf("localhost", "127.0.0.1")),
                )
                routing {
                    install(createMosaAuthenticatedRateLimitPlugin(authenticatedRateLimiter))
                    (this@ControllerHttpTestEnvironment.controllers + controllers).forEach {
                        register(it)
                    }
                }
            }
            client =
                createClient {
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            registerModule(InstantJacksonSerializers.createModule())
                            setTimeZone(TimeZone.getTimeZone("UTC"))
                        }
                    }
                    defaultRequest {
                        header(HttpHeaders.XForwardedProto, "https")
                    }
                }
            block()
        }
    }
}
