package straightway.mosa.defense.ratelimit

data class IpRateLimitConfig(
    val permitsPerMinute: Int,
    val burst: Int,
    val maxEntries: Int = 10_000,
) {
    init {
        require(permitsPerMinute > 0) { "permitsPerMinute must be positive" }
        require(burst > 0) { "burst must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }
}
