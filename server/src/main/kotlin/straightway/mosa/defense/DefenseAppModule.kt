package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.onClose
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.NanoClock
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.requestduration.RequestDurationForRouteState
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestduration.RequestDurationSheddingConfig
import straightway.mosa.defense.requestduration.RequestDurationSheddingRatioMapper
import straightway.mosa.identity.IdentityLogEntryFormatter
import java.nio.file.Path
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

val approvegidRateLimit: Qualifier = named("approvegidRateLimit")
val authenticatedRateLimit: Qualifier = named("authenticatedRateLimit")
val ipRateLimit: Qualifier = named("ipRateLimit")
val ipBlockList: Qualifier = named("ipBlockList")
val identityBlockList: Qualifier = named("identityBlockList")

fun Module.configureDefenseAppModule() {
    single<MeterRegistry> { SimpleMeterRegistry() } onClose { it?.close() }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val minSamplesForBaseline =
            cliArguments.getInt(DefenseCommandLineOptions.REQUEST_DURATION_MIN_SAMPLES)?.toLong()
                ?: 200L
        val minBaselineMs =
            cliArguments.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_MIN_BASELINE_MS)
                ?: 1.0
        val ratioSlack =
            cliArguments.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_RATIO_SLACK)
                ?: 0.15
        val fullSheddingRatio =
            cliArguments.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_FULL_SHEDDING_RATIO)
                ?: 2.0
        val sheddingSampleAlpha =
            cliArguments.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_ALPHA)
                ?: 0.2
        val sheddingDecayAlpha =
            cliArguments.getDouble(DefenseCommandLineOptions.REQUEST_DURATION_DECAY_ALPHA)
                ?: 0.2
        val sampleIntervalMs =
            cliArguments.getInt(DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_INTERVAL_MS)
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
    single {
        RequestDurationForRouteState.Factory { timer ->
            RequestDurationForRouteState(get(), get(), timer, get())
        }
    }
    single { RequestDurationForRouteStateRegistry(get(), get()) }
    single { Random(get<NanoClock>().nanoTime()) }
    single(approvegidRateLimit) {
        val cliArguments: CommandLineArgumentStore = get()
        val permitsPerMinute =
            cliArguments.getInt(DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PER_MINUTE)
                ?: 60
        val burst = cliArguments.getInt(DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_BURST) ?: 20
        val penaltySeconds =
            cliArguments.getInt(DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS)?.toLong()
                ?: 15L
        val retryAfterJitterSeconds =
            cliArguments.getInt(DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS)?.toLong()
                ?: 1L
        RateLimitConfig(
            permitsPerMinute = permitsPerMinute,
            burst = burst,
            penaltySeconds = penaltySeconds,
            retryAfterJitterSeconds = retryAfterJitterSeconds,
        )
    }
    single(authenticatedRateLimit) {
        val cliArguments: CommandLineArgumentStore = get()
        val permitsPerMinute =
            cliArguments.getInt(DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PER_MINUTE)
                ?: 60
        val burst = cliArguments.getInt(DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_BURST) ?: 20
        val penaltySeconds =
            cliArguments.getInt(DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS)?.toLong()
                ?: 5L
        val retryAfterJitterSeconds =
            cliArguments.getInt(DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS)?.toLong()
                ?: 1L
        RateLimitConfig(
            permitsPerMinute = permitsPerMinute,
            burst = burst,
            penaltySeconds = penaltySeconds,
            retryAfterJitterSeconds = retryAfterJitterSeconds,
        )
    }
    single(ipRateLimit) {
        val cliArguments: CommandLineArgumentStore = get()
        val permitsPerMinute =
            cliArguments.getInt(DefenseCommandLineOptions.IP_RATE_LIMIT_PER_MINUTE)
                ?: 600
        val burst = cliArguments.getInt(DefenseCommandLineOptions.IP_RATE_LIMIT_BURST) ?: 200
        val penaltySeconds =
            cliArguments.getInt(DefenseCommandLineOptions.IP_RATE_LIMIT_PENALTY_SECONDS)?.toLong()
                ?: 10L
        val retryAfterJitterSeconds =
            cliArguments.getInt(DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS)?.toLong()
                ?: 1L
        RateLimitConfig(
            permitsPerMinute = permitsPerMinute,
            burst = burst,
            penaltySeconds = penaltySeconds,
            retryAfterJitterSeconds = retryAfterJitterSeconds,
        )
    }
    single(approvegidRateLimit) {
        RateLimiter(get(), get(approvegidRateLimit))
    }
    single(authenticatedRateLimit) {
        RateLimiter(get(), get(authenticatedRateLimit))
    }
    single(ipRateLimit) {
        RateLimiter(get(), get(ipRateLimit))
    }
    single(ipBlockList) {
        AutoRefreshingFileBackedContent(
            AutoRefreshingFileBackedContent.Config(
                path = Path.of("mosa_ip_blocklist.yaml"),
                refreshInterval = 5.seconds,
            ),
            get(),
            onError = { error -> klogger.error(error) { "mosa_ip_blocklist has invalid format" } },
        ) {
            ClientBlockList.transformer.transform(it).also { blockList ->
                klogger.info {
                    "blocked client IP addresses: ${blockList.blockedClients.joinToString(", ")}"
                }
            }
        }
    }
    single(identityBlockList) {
        AutoRefreshingFileBackedContent(
            AutoRefreshingFileBackedContent.Config(
                path = Path.of("mosa_identity_blocklist.yaml"),
                refreshInterval = 5.seconds,
            ),
            get(),
            onError = { error -> klogger.error(error) { "mosa_identity_blocklist has invalid format" } },
        ) {
            IdentityBlockList.transformer.transform(it).also { blockList ->
                val entries = blockList.blockedGids.map { gid -> IdentityLogEntryFormatter.format(gid) }
                klogger.info {
                    "blocked gids: ${entries.joinToString(", ")}"
                }
            }
        }
    }
}

private val klogger = KotlinLogging.logger {}
