package straightway.mosa.defense

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCli::class)
class DefenseCommandLineOptions(
    command: Subcommand,
) {
    val loadSheddingStart by
        command
            .option(
                ArgType.Double,
                fullName = CPU_LOAD_SHEDDING_START,
                description = "Normalized CPU load where shedding starts (0.0-1.0)",
            ).default(0.85)
    val loadSheddingFull by
        command
            .option(
                ArgType.Double,
                fullName = CPU_LOAD_SHEDDING_FULL,
                description = "Normalized CPU load where shedding is full (0.0-1.0)",
            ).default(0.95)
    val cpuLoadSmoothingHalfLifeSeconds by
        command
            .option(
                ArgType.Double,
                fullName = CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS,
                description = "Half-life in seconds for CPU load smoothing",
            ).default(10.0)
    val memorySheddingHeapStart by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_HEAP_START,
                description = "Heap usage where shedding starts (0.0-1.0)",
            ).default(0.9)
    val memorySheddingHeapFull by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_HEAP_FULL,
                description = "Heap usage where shedding is full (0.0-1.0)",
            ).default(0.98)
    val memorySheddingNonHeapStart by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_NONHEAP_START,
                description = "Non-heap usage where shedding starts (0.0-1.0)",
            ).default(0.98)
    val memorySheddingNonHeapFull by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_NONHEAP_FULL,
                description = "Non-heap usage where shedding is full (0.0-1.0)",
            ).default(0.999)
    val memorySheddingSystemStart by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_SYSTEM_START,
                description = "System memory usage where shedding starts (0.0-1.0)",
            ).default(0.85)
    val memorySheddingSystemFull by
        command
            .option(
                ArgType.Double,
                fullName = MEMORY_SHEDDING_SYSTEM_FULL,
                description = "System memory usage where shedding is full (0.0-1.0)",
            ).default(0.93)
    val loadSheddingRetryAfterSeconds by
        command
            .option(
                ArgType.Int,
                fullName = CPU_LOAD_SHEDDING_RETRY_AFTER,
                description = "Seconds to publish in the Retry-After header when shedding",
            ).default(30)
    val requestDurationMinSamples by
        command
            .option(
                ArgType.Int,
                fullName = REQUEST_DURATION_MIN_SAMPLES,
                description = "Minimum samples required before request duration shedding starts",
            ).default(200)
    val requestDurationMinBaselineMs by
        command
            .option(
                ArgType.Double,
                fullName = REQUEST_DURATION_MIN_BASELINE_MS,
                description = "Minimum baseline latency in milliseconds for request duration shedding",
            ).default(1.0)
    val requestDurationRatioSlack by
        command
            .option(
                ArgType.Double,
                fullName = REQUEST_DURATION_RATIO_SLACK,
                description = "Ratio slack before request duration shedding ramps up",
            ).default(0.15)
    val requestDurationFullSheddingRatio by
        command
            .option(
                ArgType.Double,
                fullName = REQUEST_DURATION_FULL_SHEDDING_RATIO,
                description = "Ratio at which request duration shedding is full",
            ).default(2.0)
    val requestDurationSampleAlpha by
        command
            .option(
                ArgType.Double,
                fullName = REQUEST_DURATION_SAMPLE_ALPHA,
                description = "EWMA alpha for request duration shedding samples",
            ).default(0.2)
    val requestDurationDecayAlpha by
        command
            .option(
                ArgType.Double,
                fullName = REQUEST_DURATION_DECAY_ALPHA,
                description = "EWMA alpha for request duration shedding decay",
            ).default(0.2)
    val requestDurationSampleIntervalMs by
        command
            .option(
                ArgType.Int,
                fullName = REQUEST_DURATION_SAMPLE_INTERVAL_MS,
                description = "Sampling interval in milliseconds for request duration shedding",
            ).default(250)
    val podCertificateValiditySeconds by
        command
            .option(
                ArgType.Int,
                fullName = POD_CERT_VALIDITY_SECONDS,
                description = "Validity duration for pod certificates in seconds",
            ).default(7.days.inWholeSeconds.toInt())
    val approveGidRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = APPROVE_GID_RATE_LIMIT_PER_MINUTE,
                description = "Allowed approvegid requests per minute per IP (positive value)",
            ).default(60)
    val approveGidRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = APPROVE_GID_RATE_LIMIT_BURST,
                description = "Burst size for approvegid rate limiting per IP (positive value)",
            ).default(20)
    val approveGidRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for approvegid rate limit blocks (non-negative value)",
            ).default(15)
    val rateLimitRetryAfterJitterSeconds by
        command
            .option(
                ArgType.Int,
                fullName = RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS,
                description = "Maximum Retry-After jitter in seconds for rate limit responses (non-negative value)",
            ).default(1)
    val ipRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = IP_RATE_LIMIT_PER_MINUTE,
                description = "Allowed API requests per minute per IP (positive value)",
            ).default(600)
    val ipRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = IP_RATE_LIMIT_BURST,
                description = "Burst size for API rate limiting per IP (positive value)",
            ).default(200)
    val ipRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = IP_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for IP rate limit blocks (non-negative value)",
            ).default(10)
    val authenticatedRateLimitPerMinute by
        command
            .option(
                ArgType.Int,
                fullName = AUTHENTICATED_RATE_LIMIT_PER_MINUTE,
                description = "Allowed authenticated requests per minute per gid (positive value)",
            ).default(60)
    val authenticatedRateLimitBurst by
        command
            .option(
                ArgType.Int,
                fullName = AUTHENTICATED_RATE_LIMIT_BURST,
                description = "Burst size for authenticated rate limiting per gid (positive value)",
            ).default(20)
    val authenticatedRateLimitPenaltySeconds by
        command
            .option(
                ArgType.Int,
                fullName = AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS,
                description = "Penalty duration in seconds for authenticated rate limit blocks (non-negative value)",
            ).default(5)
    val maxRequestBodyBytes by
        command.option(
            ArgType.Int,
            fullName = MAX_REQUEST_BODY_BYTES,
            description = "Maximum size for request bodies in bytes",
        )

    fun toArgumentValues(): Map<String, Any?> =
        mapOf(
            CPU_LOAD_SHEDDING_START to loadSheddingStart,
            CPU_LOAD_SHEDDING_FULL to loadSheddingFull,
            CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS to cpuLoadSmoothingHalfLifeSeconds,
            MEMORY_SHEDDING_HEAP_START to memorySheddingHeapStart,
            MEMORY_SHEDDING_HEAP_FULL to memorySheddingHeapFull,
            MEMORY_SHEDDING_NONHEAP_START to memorySheddingNonHeapStart,
            MEMORY_SHEDDING_NONHEAP_FULL to memorySheddingNonHeapFull,
            MEMORY_SHEDDING_SYSTEM_START to memorySheddingSystemStart,
            MEMORY_SHEDDING_SYSTEM_FULL to memorySheddingSystemFull,
            CPU_LOAD_SHEDDING_RETRY_AFTER to loadSheddingRetryAfterSeconds,
            REQUEST_DURATION_MIN_SAMPLES to requestDurationMinSamples,
            REQUEST_DURATION_MIN_BASELINE_MS to requestDurationMinBaselineMs,
            REQUEST_DURATION_RATIO_SLACK to requestDurationRatioSlack,
            REQUEST_DURATION_FULL_SHEDDING_RATIO to requestDurationFullSheddingRatio,
            REQUEST_DURATION_SAMPLE_ALPHA to requestDurationSampleAlpha,
            REQUEST_DURATION_DECAY_ALPHA to requestDurationDecayAlpha,
            REQUEST_DURATION_SAMPLE_INTERVAL_MS to requestDurationSampleIntervalMs,
            POD_CERT_VALIDITY_SECONDS to podCertificateValiditySeconds,
            APPROVE_GID_RATE_LIMIT_PER_MINUTE to approveGidRateLimitPerMinute,
            APPROVE_GID_RATE_LIMIT_BURST to approveGidRateLimitBurst,
            APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS to approveGidRateLimitPenaltySeconds,
            RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS to rateLimitRetryAfterJitterSeconds,
            IP_RATE_LIMIT_PER_MINUTE to ipRateLimitPerMinute,
            IP_RATE_LIMIT_BURST to ipRateLimitBurst,
            IP_RATE_LIMIT_PENALTY_SECONDS to ipRateLimitPenaltySeconds,
            AUTHENTICATED_RATE_LIMIT_PER_MINUTE to authenticatedRateLimitPerMinute,
            AUTHENTICATED_RATE_LIMIT_BURST to authenticatedRateLimitBurst,
            AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS to authenticatedRateLimitPenaltySeconds,
            MAX_REQUEST_BODY_BYTES to maxRequestBodyBytes,
        )

    companion object {
        const val CPU_LOAD_SHEDDING_START = "cpu-load-shedding-start"
        const val CPU_LOAD_SHEDDING_FULL = "cpu-load-shedding-full"
        const val CPU_LOAD_SMOOTHING_HALF_LIFE_SECONDS = "cpu-load-smoothing-half-life-seconds"
        const val CPU_LOAD_SHEDDING_RETRY_AFTER = "cpu-load-shedding-retry-after"
        const val MEMORY_SHEDDING_HEAP_START = "memory-shedding-heap-start"
        const val MEMORY_SHEDDING_HEAP_FULL = "memory-shedding-heap-full"
        const val MEMORY_SHEDDING_NONHEAP_START = "memory-shedding-nonheap-start"
        const val MEMORY_SHEDDING_NONHEAP_FULL = "memory-shedding-nonheap-full"
        const val MEMORY_SHEDDING_SYSTEM_START = "memory-shedding-system-start"
        const val MEMORY_SHEDDING_SYSTEM_FULL = "memory-shedding-system-full"
        const val REQUEST_DURATION_MIN_SAMPLES = "request-duration-shedding-min-samples"
        const val REQUEST_DURATION_MIN_BASELINE_MS = "request-duration-shedding-min-baseline-ms"
        const val REQUEST_DURATION_RATIO_SLACK = "request-duration-shedding-ratio-slack"
        const val REQUEST_DURATION_FULL_SHEDDING_RATIO = "request-duration-shedding-full-ratio"
        const val REQUEST_DURATION_SAMPLE_ALPHA = "request-duration-shedding-sample-alpha"
        const val REQUEST_DURATION_DECAY_ALPHA = "request-duration-shedding-decay-alpha"
        const val REQUEST_DURATION_SAMPLE_INTERVAL_MS = "request-duration-shedding-sample-interval-ms"
        const val POD_CERT_VALIDITY_SECONDS = "pod-cert-validity-seconds"
        const val APPROVE_GID_RATE_LIMIT_PER_MINUTE = "approvegid-rate-limit-per-minute"
        const val APPROVE_GID_RATE_LIMIT_BURST = "approvegid-rate-limit-burst"
        const val APPROVE_GID_RATE_LIMIT_PENALTY_SECONDS = "approvegid-rate-limit-penalty-seconds"
        const val RATE_LIMIT_RETRY_AFTER_JITTER_SECONDS = "rate-limit-retry-after-jitter-seconds"
        const val IP_RATE_LIMIT_PER_MINUTE = "ip-rate-limit-per-minute"
        const val IP_RATE_LIMIT_BURST = "ip-rate-limit-burst"
        const val IP_RATE_LIMIT_PENALTY_SECONDS = "ip-rate-limit-penalty-seconds"
        const val AUTHENTICATED_RATE_LIMIT_PER_MINUTE = "authenticated-rate-limit-per-minute"
        const val AUTHENTICATED_RATE_LIMIT_BURST = "authenticated-rate-limit-burst"
        const val AUTHENTICATED_RATE_LIMIT_PENALTY_SECONDS = "authenticated-rate-limit-penalty-seconds"
        const val MAX_REQUEST_BODY_BYTES = "max-request-body-bytes"
    }
}
