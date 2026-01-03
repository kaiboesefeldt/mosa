package straightway.mosa.defense.ratelimit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import straightway.mosa.NanoClock
import java.util.LinkedHashMap
import kotlin.math.max
import kotlin.math.min

class IpRateLimiter(
    private val nanoClock: NanoClock,
    private val config: IpRateLimitConfig,
) {
    private val retryDurations = (60L / config.permitsPerMinute).coerceAtLeast(1L) * 2L
    private val mutex = Mutex()
    private val buckets = LruBucketMap(config.maxEntries)

    suspend fun check(ip: String) =
        mutex
            .withLock {
                check(ip, nanoClock.nanoTime())
            }.let { allowed ->
                if (allowed) {
                    RateLimitDecision.allowed()
                } else {
                    RateLimitDecision.rejected(retryDurations)
                }
            }

    private fun check(
        ip: String,
        nowNanos: Long,
    ): Boolean {
        val (nextState, allowed) =
            buckets[ip]?.run {
                refill(nowNanos).run { consume() to allowed }
            } ?: (BucketState(tokens = config.burst - 1, lastRefillAtNanos = nowNanos) to true)
        buckets[ip] = nextState
        return allowed
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
    )

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
