package straightway.mosa.defense.ratelimit

data class RateLimitDecision(
    val allowed: Boolean,
    val retryAfterSeconds: Long,
) {
    companion object {
        fun allowed(): RateLimitDecision = RateLimitDecision(true, 0)

        fun rejected(retryAfterSeconds: Long): RateLimitDecision = RateLimitDecision(false, retryAfterSeconds)
    }
}
