package straightway.mosa.defense.ratelimit

data class RateLimitConfig(
    val permitsPerMinute: Int,
    val burst: Int,
    val maxEntries: Int = 10_000,
    val penaltySeconds: Long = 0,
    val retryAfterJitterSeconds: Long = 1,
) {
    init {
        require(0 < permitsPerMinute) { "permitsPerMinute must be positive" }
        require(0 < burst) { "burst must be positive" }
        require(0 < maxEntries) { "maxEntries must be positive" }
        require(0 <= penaltySeconds) { "penaltySeconds must be non-negative" }
        require(0 <= retryAfterJitterSeconds) { "retryAfterJitterSeconds must be non-negative" }
    }
}
