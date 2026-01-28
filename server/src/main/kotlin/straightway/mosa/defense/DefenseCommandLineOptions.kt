package straightway.mosa.defense

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import straightway.mosa.ServerCommandLineArguments
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCli::class)
class DefenseCommandLineOptions(
    command: Subcommand,
) {
    val loadSheddingStart by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.CPU_LOAD_SHEDDING_START,
                description = "Normalized CPU load where shedding starts (0.0-1.0)",
            ).default(0.85)
    val loadSheddingFull by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.CPU_LOAD_SHEDDING_FULL,
                description = "Normalized CPU load where shedding is full (0.0-1.0)",
            ).default(0.95)
    val cpuLoadSmoothingHalfLifeSeconds by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS,
                description = "Half-life in seconds for CPU load smoothing",
            ).default(10.0)
    val memorySheddingHeapStart by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_START,
                description = "Heap usage where shedding starts (0.0-1.0)",
            ).default(0.9)
    val memorySheddingHeapFull by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_FULL,
                description = "Heap usage where shedding is full (0.0-1.0)",
            ).default(0.98)
    val memorySheddingNonHeapStart by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_START,
                description = "Non-heap usage where shedding starts (0.0-1.0)",
            ).default(0.98)
    val memorySheddingNonHeapFull by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_FULL,
                description = "Non-heap usage where shedding is full (0.0-1.0)",
            ).default(0.999)
    val memorySheddingSystemStart by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_START,
                description = "System memory usage where shedding starts (0.0-1.0)",
            ).default(0.85)
    val memorySheddingSystemFull by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_FULL,
                description = "System memory usage where shedding is full (0.0-1.0)",
            ).default(0.93)
    val loadSheddingRetryAfterSeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.CPU_LOAD_SHEDDING_RETRY_AFTER,
                description = "Seconds to publish in the Retry-After header when shedding",
            ).default(30)
    val requestDurationMinSamples by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES,
                description = "Minimum samples required before request duration shedding starts",
            ).default(200)
    val requestDurationMinBaselineMs by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS,
                description = "Minimum baseline latency in milliseconds for request duration shedding",
            ).default(1.0)
    val requestDurationRatioSlack by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK,
                description = "Ratio slack before request duration shedding ramps up",
            ).default(0.15)
    val requestDurationFullSheddingRatio by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO,
                description = "Ratio at which request duration shedding is full",
            ).default(2.0)
    val requestDurationSampleAlpha by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA,
                description = "EWMA alpha for request duration shedding samples",
            ).default(0.2)
    val requestDurationDecayAlpha by
        command
            .option(
                ArgType.Double,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA,
                description = "EWMA alpha for request duration shedding decay",
            ).default(0.2)
    val requestDurationSampleIntervalMs by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS,
                description = "Sampling interval in milliseconds for request duration shedding",
            ).default(250)
    val podCertificateValiditySeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.POD_CERT_VALIDITY_SECONDS,
                description = "Validity duration for pod certificates in seconds",
            ).default(7.days.inWholeSeconds.toInt())
    val approveGidRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PER_MINUTE,
                description = "Allowed approvegid requests per minute per IP (positive value)",
            ).default(60)
    val approveGidRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_BURST,
                description = "Burst size for approvegid rate limiting per IP (positive value)",
            ).default(20)
    val approveGidRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for approvegid rate limit blocks (non-negative value)",
            ).default(15)
    val rateLimitRetryAfterJitterSeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS,
                description = "Maximum Retry-After jitter in seconds for rate limit responses (non-negative value)",
            ).default(1)
    val ipRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.IP_RATE_LIMIT_PER_MINUTE,
                description = "Allowed API requests per minute per IP (positive value)",
            ).default(600)
    val ipRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.IP_RATE_LIMIT_BURST,
                description = "Burst size for API rate limiting per IP (positive value)",
            ).default(200)
    val ipRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.IP_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for IP rate limit blocks (non-negative value)",
            ).default(10)
    val authenticatedRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PER_MINUTE,
                description = "Allowed authenticated requests per minute per gid (positive value)",
            ).default(60)
    val authenticatedRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_BURST,
                description = "Burst size for authenticated rate limiting per gid (positive value)",
            ).default(20)
    val authenticatedRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for authenticated rate limit blocks (non-negative value)",
            ).default(5)
    val maxRequestBodyBytes by
        command.option(
            ArgType.Int,
            fullName = ServerCommandLineArguments.MAX_REQUEST_BODY_BYTES,
            description = "Maximum size for request bodies in bytes",
        )

    fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            ServerCommandLineArguments.CPU_LOAD_SHEDDING_START to loadSheddingStart,
            ServerCommandLineArguments.CPU_LOAD_SHEDDING_FULL to loadSheddingFull,
            ServerCommandLineArguments.CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS to cpuLoadSmoothingHalfLifeSeconds,
            ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_START to memorySheddingHeapStart,
            ServerCommandLineArguments.MEMORY_SHEDDING_HEAP_FULL to memorySheddingHeapFull,
            ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_START to memorySheddingNonHeapStart,
            ServerCommandLineArguments.MEMORY_SHEDDING_NONHEAP_FULL to memorySheddingNonHeapFull,
            ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_START to memorySheddingSystemStart,
            ServerCommandLineArguments.MEMORY_SHEDDING_SYSTEM_FULL to memorySheddingSystemFull,
            ServerCommandLineArguments.CPU_LOAD_SHEDDING_RETRY_AFTER to loadSheddingRetryAfterSeconds,
            ServerCommandLineArguments.REQUEST_DURATION_MIN_SAMPLES to requestDurationMinSamples,
            ServerCommandLineArguments.REQUEST_DURATION_MIN_BASELINE_MS to requestDurationMinBaselineMs,
            ServerCommandLineArguments.REQUEST_DURATION_RATIO_SLACK to requestDurationRatioSlack,
            ServerCommandLineArguments.REQUEST_DURATION_FULL_SHEDDING_RATIO to requestDurationFullSheddingRatio,
            ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_ALPHA to requestDurationSampleAlpha,
            ServerCommandLineArguments.REQUEST_DURATION_DECAY_ALPHA to requestDurationDecayAlpha,
            ServerCommandLineArguments.REQUEST_DURATION_SAMPLE_INTERVAL_MS to requestDurationSampleIntervalMs,
            ServerCommandLineArguments.POD_CERT_VALIDITY_SECONDS to podCertificateValiditySeconds,
            ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PER_MINUTE to approveGidRateLimitPerMinute,
            ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_BURST to approveGidRateLimitBurst,
            ServerCommandLineArguments.APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS to approveGidRateLimitPenaltySeconds,
            ServerCommandLineArguments.RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS to rateLimitRetryAfterJitterSeconds,
            ServerCommandLineArguments.IP_RATE_LIMIT_PER_MINUTE to ipRateLimitPerMinute,
            ServerCommandLineArguments.IP_RATE_LIMIT_BURST to ipRateLimitBurst,
            ServerCommandLineArguments.IP_RATE_LIMIT_PENALTY_SECONDS to ipRateLimitPenaltySeconds,
            ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PER_MINUTE to authenticatedRateLimitPerMinute,
            ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_BURST to authenticatedRateLimitBurst,
            ServerCommandLineArguments.AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS to authenticatedRateLimitPenaltySeconds,
            ServerCommandLineArguments.MAX_REQUEST_BODY_BYTES to maxRequestBodyBytes,
        )
}
