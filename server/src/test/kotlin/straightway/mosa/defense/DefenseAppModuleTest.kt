package straightway.mosa.defense

import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.NanoClock
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.defense.core.LoadSheddingConfig
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.SheddingRequest
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestduration.RequestDurationSheddingConfig
import straightway.mosa.defense.requestduration.RequestDurationSheddingRatioMapper
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DefenseAppModuleTest {
    @Test
    fun `configures defense module with defaults`() {
        withBlockListFiles {
            val clock = MutableClock(Instant.EPOCH)
            val koinApplication =
                buildKoin(
                    CommandLineArgumentStore.EMPTY,
                    clock,
                )
            try {
                val koin = koinApplication.koin
                val meterRegistry = koin.get<MeterRegistry>()
                assertNotNull(koin.get<RequestDurationSheddingConfig>())
                assertNotNull(koin.get<RequestDurationSheddingRatioMapper>())
                assertNotNull(koin.get<RequestDurationForRouteStateRegistry>())
                assertIs<Random>(koin.get<Random>())
                assertNotNull(koin.get<LoadSheddingConfig>())
                assertNotNull(koin.get<RequestBodyLimitConfig>())
                assertRateLimitConfigDefaults(koin, approvegidRateLimit, 60, 20, 15L, 1L)
                assertRateLimitConfigDefaults(koin, authenticatedRateLimit, 60, 20, 5L, 1L)
                assertRateLimitConfigDefaults(koin, ipRateLimit, 600, 200, 10L, 1L)
                assertNotNull(koin.get<RateLimiter>(approvegidRateLimit))
                assertNotNull(koin.get<RateLimiter>(authenticatedRateLimit))
                assertNotNull(koin.get<RateLimiter>(ipRateLimit))
                val ipBlockListContent = koin.get<AutoRefreshingFileBackedContent<ClientBlockList>>(ipBlockList)
                val identityBlockListContent = koin.get<AutoRefreshingFileBackedContent<IdentityBlockList>>(identityBlockList)
                meterRegistry.timer(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME, "method", "GET", "route", "/health")
                koin
                    .get<RequestDurationForRouteStateRegistry>()
                    .stateFor(SheddingRequest("GET", "/health"))
                clock.advanceSeconds(10)
                Files.writeString(Path.of("mosa_ip_blocklist.yaml"), "blockedClients: [\"invalid-ip\"]\n")
                runBlocking { ipBlockListContent.get() }
                Files.writeString(Path.of("mosa_identity_blocklist.yaml"), "blockedGids: [\n")
                runBlocking { identityBlockListContent.get() }
            } finally {
                koinApplication.close()
            }
        }
    }

    @Test
    fun `configures defense module with cli overrides`() {
        withBlockListFiles {
            val clock = MutableClock(Instant.EPOCH)
            val koinApplication =
                buildKoin(
                    CommandLineArgumentStore.from { name ->
                        when (name) {
                            DefenseCommandLineOptions.REQUEST_DURATION_MIN_SAMPLES -> 10
                            DefenseCommandLineOptions.REQUEST_DURATION_MIN_BASELINE_MS -> 2.5
                            DefenseCommandLineOptions.REQUEST_DURATION_RATIO_SLACK -> 0.3
                            DefenseCommandLineOptions.REQUEST_DURATION_FULL_SHEDDING_RATIO -> 1.5
                            DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_ALPHA -> 0.4
                            DefenseCommandLineOptions.REQUEST_DURATION_DECAY_ALPHA -> 0.6
                            DefenseCommandLineOptions.REQUEST_DURATION_SAMPLE_INTERVAL_MS -> 500
                            DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PER_MINUTE -> 5
                            DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_BURST -> 6
                            DefenseCommandLineOptions.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS -> 7
                            DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PER_MINUTE -> 8
                            DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_BURST -> 9
                            DefenseCommandLineOptions.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS -> 11
                            DefenseCommandLineOptions.IP_RATE_LIMIT_PER_MINUTE -> 12
                            DefenseCommandLineOptions.IP_RATE_LIMIT_BURST -> 13
                            DefenseCommandLineOptions.IP_RATE_LIMIT_PENALTY_SECONDS -> 14
                            DefenseCommandLineOptions.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS -> 3
                            else -> null
                        }
                    },
                    clock,
                )
            try {
                val koin = koinApplication.koin
                val config = koin.get<RequestDurationSheddingConfig>()
                assertEquals(10L, config.minSamplesForBaseline)
                assertEquals(2.5, config.minBaselineMs)
                assertEquals(0.3, config.ratioSlack)
                assertEquals(1.5, config.fullSheddingRatio)
                assertEquals(0.4, config.sheddingSampleAlpha)
                assertEquals(0.6, config.sheddingDecayAlpha)
                assertEquals(500_000_000L, config.sampleIntervalNs)
                assertRateLimitConfigDefaults(koin, approvegidRateLimit, 5, 6, 7L, 3L)
                assertRateLimitConfigDefaults(koin, authenticatedRateLimit, 8, 9, 11L, 3L)
                assertRateLimitConfigDefaults(koin, ipRateLimit, 12, 13, 14L, 3L)
                val ipBlockListContent = koin.get<AutoRefreshingFileBackedContent<ClientBlockList>>(ipBlockList)
                val identityBlockListContent = koin.get<AutoRefreshingFileBackedContent<IdentityBlockList>>(identityBlockList)
                clock.advanceSeconds(10)
                Files.writeString(Path.of("mosa_ip_blocklist.yaml"), "blockedClients: [\"invalid-ip\"]\n")
                runBlocking { ipBlockListContent.get() }
                Files.writeString(Path.of("mosa_identity_blocklist.yaml"), "blockedGids: [\n")
                runBlocking { identityBlockListContent.get() }
            } finally {
                koinApplication.close()
            }
        }
    }

    private fun buildKoin(
        arguments: CommandLineArgumentStore,
        clock: Clock,
    ): KoinApplication =
        koinApplication {
            modules(
                module {
                    single { arguments }
                    single { clock }
                    single { NanoClock { 0L } }
                    configureDefenseAppModule()
                },
            )
        }

    private fun assertRateLimitConfigDefaults(
        koin: Koin,
        qualifier: Qualifier,
        permitsPerMinute: Int,
        burst: Int,
        penaltySeconds: Long,
        retryAfterJitterSeconds: Long,
    ) {
        val config = koin.get<RateLimitConfig>(qualifier)
        assertEquals(permitsPerMinute, config.permitsPerMinute)
        assertEquals(burst, config.burst)
        assertEquals(penaltySeconds, config.penaltySeconds)
        assertEquals(retryAfterJitterSeconds, config.retryAfterJitterSeconds)
    }

    private fun withBlockListFiles(block: () -> Unit) {
        val ipPath = Path.of("mosa_ip_blocklist.yaml")
        val identityPath = Path.of("mosa_identity_blocklist.yaml")
        Files.writeString(
            ipPath,
            """
            blockedClients:
              - "127.0.0.1"
            """.trimIndent() + "\n",
        )
        Files.writeString(
            identityPath,
            """
            blockedGids:
              - "test-gid"
            """.trimIndent() + "\n",
        )
        try {
            block()
        } finally {
            Files.deleteIfExists(ipPath)
            Files.deleteIfExists(identityPath)
        }
    }

    private class MutableClock(
        private var instant: Instant,
        private val zoneId: ZoneId = ZoneId.systemDefault(),
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        override fun instant(): Instant = instant

        fun advanceSeconds(seconds: Long) {
            instant = instant.plus(seconds, ChronoUnit.SECONDS)
        }
    }
}
