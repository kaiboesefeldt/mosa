package straightway.mosa.defense.ratelimit

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RateLimitConfigTest {
    @Test
    fun `rejects non-positive permits per minute`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitConfig(permitsPerMinute = 0, burst = 1)
        }
    }

    @Test
    fun `rejects non-positive burst`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitConfig(permitsPerMinute = 1, burst = 0)
        }
    }

    @Test
    fun `rejects non-positive max entries`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitConfig(permitsPerMinute = 1, burst = 1, maxEntries = 0)
        }
    }
}
