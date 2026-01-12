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
    private val tokenIntervalNanos = (NANOS_PER_MINUTE / config.permitsPerMinute).coerceAtLeast(1L)
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
        val decision = refreshedState.decideRateLimiting(nowNanos, key)
        buckets[key] = refreshedState.run { if (decision.allowed) consume() else penaltyState(nowNanos) }
        return decision
    }

    private fun BucketState?.refreshedUntil(nowNanos: Long) =
        when {
            this == null -> BucketState(tokens = config.burst, lastRefillAtNanos = nowNanos)
            isPenaltyActive(nowNanos) -> this
            else -> refill(nowNanos)
        }

    private fun BucketState.decideRateLimiting(
        nowNanos: Long,
        key: String,
    ): RateLimitDecision =
        when {
            isPenaltyActive(nowNanos) ->
                RateLimitDecision.rejected(retryAfterSecondsDuringPenalty(nowNanos, key))
            allowed ->
                RateLimitDecision.allowed()
            else ->
                RateLimitDecision.rejected(retryAfterSecondsForEmptyBucket(nowNanos, key))
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

    private fun BucketState.retryAfterSecondsDuringPenalty(
        nowNanos: Long,
        key: String,
    ): Long {
        val remainingPenaltyNanos = (penaltyUntilNanos - nowNanos).coerceAtLeast(0L)
        val baseSeconds = (remainingPenaltyNanos + tokenIntervalNanos).toCeilSeconds().coerceAtLeast(1L)
        return baseSeconds + jitterSeconds(key)
    }

    private fun BucketState.retryAfterSecondsForEmptyBucket(
        nowNanos: Long,
        key: String,
    ): Long {
        val waitForTokenSeconds = timeUntilNextTokenNanos(nowNanos).toCeilSeconds().coerceAtLeast(1L)
        val baseSeconds = max(waitForTokenSeconds, config.penaltySeconds)
        return baseSeconds + jitterSeconds(key)
    }

    private fun BucketState.timeUntilNextTokenNanos(nowNanos: Long): Long {
        val elapsedNanos = max(0L, nowNanos - lastRefillAtNanos)
        return (tokenIntervalNanos - elapsedNanos).coerceAtLeast(0L)
    }

    private fun Long.toCeilSeconds(): Long = (this + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND

    private fun jitterSeconds(key: String): Long {
        if (config.retryAfterJitterSeconds <= 0L) {
            return 0L
        }
        return (key.hashCode().toLong() and Long.MAX_VALUE) % (config.retryAfterJitterSeconds + 1)
    }

    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val NANOS_PER_MINUTE = 60L * NANOS_PER_SECOND
    }

    private class LruBucketMap(
        private val maxEntries: Int,
    ) : LinkedHashMap<String, BucketState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BucketState>): Boolean = maxEntries < size
    }
}
