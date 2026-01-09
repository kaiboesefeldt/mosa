package straightway.mosa.defense.ratelimit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import straightway.mosa.NanoClock
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

class RateLimiter(
    private val nanoClock: NanoClock,
    private val config: RateLimitConfig,
) {
    private val secondsToGainOneToken get() = (60L / config.permitsPerMinute).coerceAtLeast(1L)
    private val tokenRetryAfterSeconds = secondsToGainOneToken * RETRY_SAFETY_FACTOR
    private val mutex = Mutex()
    private val buckets = LruBucketMap(config.maxEntries)
    private val penaltyDurationNanos = config.penaltySeconds * NANOS_PER_SECOND

    suspend fun check(key: String): RateLimitDecision =
        mutex.withLock {
            check(key, nanoClock.nanoTime())
        }

    private fun check(
        key: String,
        nowNanos: Long,
    ): RateLimitDecision {
        val currentState = buckets[key]
        val refreshedState = currentState.refreshedUntil(nowNanos)
        val decision = refreshedState.decideRateLimiting(nowNanos)
        buckets[key] = refreshedState.run { if (decision.allowed) consume() else penaltyState(nowNanos) }
        return decision
    }

    private fun BucketState?.refreshedUntil(nowNanos: Long) =
        when {
            this == null -> BucketState(tokens = config.burst, lastRefillAtNanos = nowNanos)
            this.isPenaltyActive(nowNanos) -> this
            else -> this.refill(nowNanos)
        }

    private fun BucketState.decideRateLimiting(nowNanos: Long): RateLimitDecision {
        val remainingPenaltySeconds = penaltyRemainingSeconds(nowNanos)
        return when {
            0L < remainingPenaltySeconds ->
                RateLimitDecision.rejected(max(tokenRetryAfterSeconds, remainingPenaltySeconds))
            allowed ->
                RateLimitDecision.allowed()
            else ->
                RateLimitDecision.rejected(max(tokenRetryAfterSeconds, config.penaltySeconds))
        }
    }

    private fun BucketState.refill(nowNanos: Long): BucketState {
        val elapsedNanos = max(0L, nowNanos - lastRefillAtNanos)
        val addedTokens = (elapsedNanos * config.permitsPerMinute / NANOS_PER_MINUTE).toInt()
        if (addedTokens <= 0) {
            return this
        }
        val tokens = min(config.burst, tokens + addedTokens)
        return copy(tokens = tokens, lastRefillAtNanos = nowNanos)
    }

    private fun BucketState.consume() = copy(tokens = max(0, tokens - 1))

    private val BucketState.allowed get() = 0 < tokens

    private data class BucketState(
        val tokens: Int,
        val lastRefillAtNanos: Long,
        val penaltyUntilNanos: Long = 0,
    )

    private fun BucketState.isPenaltyActive(nowNanos: Long) = nowNanos < penaltyUntilNanos

    private fun BucketState.shouldBePenalized(nowNanos: Long) = 0 < penaltyDurationNanos && !isPenaltyActive(nowNanos)

    private fun BucketState.penalizeUntil(penaltyUntilNanos: Long) =
        copy(penaltyUntilNanos = penaltyUntilNanos, lastRefillAtNanos = penaltyUntilNanos)

    private fun BucketState.penaltyState(nowNanos: Long) =
        takeUnless { it.shouldBePenalized(nowNanos) }
            ?: penalizeUntil(nowNanos + penaltyDurationNanos)

    private fun BucketState.penaltyRemainingSeconds(nowNanos: Long): Long {
        val remainingPenaltySeconds =
            if (penaltyUntilNanos <= nowNanos) {
                0L
            } else {
                (penaltyUntilNanos - nowNanos) / NANOS_PER_SECOND + 1
            }
        return remainingPenaltySeconds
    }

    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND
        private const val RETRY_SAFETY_FACTOR = 2L
    }

    private class LruBucketMap(
        private val maxEntries: Int,
    ) : LinkedHashMap<String, BucketState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BucketState>): Boolean = maxEntries < size
    }
}
