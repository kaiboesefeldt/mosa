package straightway.mosa

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.lucene.store.MMapDirectory
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.onClose
import straightway.mosa.auth.Auth0TokenUtil
import straightway.mosa.auth.AuthenticationController
import straightway.mosa.auth.PodCertificateService
import straightway.mosa.auth.ThreadLocalEd25519VerifierProvider
import straightway.mosa.auth.TransientEd25519Factory
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.defense.ratelimit.IpRateLimitConfig
import straightway.mosa.defense.ratelimit.IpRateLimiter
import straightway.mosa.defense.requestduration.RequestDurationForRouteState
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestduration.RequestDurationSheddingConfig
import straightway.mosa.defense.requestduration.RequestDurationSheddingRatioMapper
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneIndexAccess
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.vectorstore.LuceneQueryPlanner
import straightway.mosa.vectorstore.QueryOnRegistry
import straightway.mosa.webclient.WebClientController
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

typealias HasherFactory = () -> MessageDigest

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single<MeterRegistry> { SimpleMeterRegistry() } onClose { it?.close() }
    single<RequestDurationSheddingConfig> {
        val cliArguments: CommandLineArgumentStore = get()
        val minSamplesForBaseline =
            cliArguments.getInt(ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES)?.toLong()
                ?: 200L
        val minBaselineMs =
            cliArguments.getDouble(ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS)
                ?: 1.0
        val ratioSlack =
            cliArguments.getDouble(ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK)
                ?: 0.15
        val fullSheddingRatio =
            cliArguments.getDouble(ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO)
                ?: 2.0
        val sheddingSampleAlpha =
            cliArguments.getDouble(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA)
                ?: 0.2
        val sheddingDecayAlpha =
            cliArguments.getDouble(ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA)
                ?: 0.2
        val sampleIntervalMs =
            cliArguments.getInt(ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS)
                ?: 250
        RequestDurationSheddingConfig(
            minSamplesForBaseline = minSamplesForBaseline,
            minBaselineMs = minBaselineMs,
            ratioSlack = ratioSlack,
            fullSheddingRatio = fullSheddingRatio,
            sheddingSampleAlpha = sheddingSampleAlpha,
            sheddingDecayAlpha = sheddingDecayAlpha,
            sampleIntervalNs = sampleIntervalMs.toLong() * 1_000_000L,
        )
    }
    single { RequestDurationSheddingRatioMapper(get()) }
    single<RequestDurationForRouteState.Factory> {
        RequestDurationForRouteState.Factory { timer ->
            RequestDurationForRouteState(get(), get(), timer, get())
        }
    }
    single<RequestDurationForRouteStateRegistry> { RequestDurationForRouteStateRegistry(get(), get()) }
    single<EmbeddingService> { OnnxLocalEmbeddingService.create() } onClose { (it as? AutoCloseable)?.close() }
    single<Clock> { Clock.systemUTC() }
    single<NanoClock> { NanoClock(System::nanoTime) }
    single<Random> { Random(get<NanoClock>().nanoTime()) }
    single<LuceneIndexAccess> { LuceneIndexAccess { MMapDirectory(Paths.get("mosa_lucene_index")) } } onClose
        { (it as? AutoCloseable)?.close() }
    single<QueryOnRegistry> { QueryOnRegistry(pageSize = 10, maxNotFinishedQueries = 10000) }
    single<LuceneQueryPlanner> { LuceneQueryPlanner(maxResults = 1000) }
    single<VectorStore> {
        LuceneLocalVectorStore.create(
            indexAccess = get(),
            paginationRegistry = get(),
            queryPlanner = get(),
            clock = get<Clock>(),
            vectorSize = get<EmbeddingService>().dimension,
        )
    } onClose { (it as? AutoCloseable)?.close() }
    single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
    single<SignatureFactory> { TransientEd25519Factory() }
    single<SignatureVerifierProvider> { ThreadLocalEd25519VerifierProvider() }
    single<Crypto> { CryptoEd25519() }
    single<TokenUtil> { Auth0TokenUtil(get(), get(), get(), get(), get()) }

    single<ContentService> { ContentService(get(), get(), get()) }

    single<PodCertificateService> { PodCertificateService(get()) }

    single<CommandLineArgumentStore> { arguments }

    single<IpRateLimitConfig> {
        val cliArguments: CommandLineArgumentStore = get()
        val permitsPerMinute =
            cliArguments.getInt(ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PER_MINUTE)
                ?: 60
        val burst = cliArguments.getInt(ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_BURST) ?: 20
        IpRateLimitConfig(permitsPerMinute = permitsPerMinute, burst = burst)
    }
    single<IpRateLimiter> { IpRateLimiter(get(), get()) }

    single { ContentController(get(), get()) } bind BaseController::class
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val certificateLifetimeSeconds =
            cliArguments.getInt(ServerCommandLineArguments.POD_CERT_VALIDITY_SECONDS)
                ?: 7.days.inWholeSeconds.toInt()
        AuthenticationController(get(), get(), certificateLifetimeSeconds.toLong().seconds, get())
    } bind BaseController::class
    single {
        val cliArguments: CommandLineArgumentStore = get()
        WebClientController(
            serveSourceMaps = cliArguments.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS) == true,
            allowPlainHttp = cliArguments.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) == true,
        )
    } bind BaseController::class
}
