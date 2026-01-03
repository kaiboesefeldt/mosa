package straightway.mosa.defense.ratelimit

import kotlin.test.Test
import kotlin.test.assertFailsWith

class IpRateLimitConfigTest {
    @Test
    fun `rejects non-positive permits per minute`() {
        assertFailsWith<IllegalArgumentException> {
            IpRateLimitConfig(permitsPerMinute = 0, burst = 1)
        }
    }

    @Test
    fun `rejects non-positive burst`() {
        assertFailsWith<IllegalArgumentException> {
            IpRateLimitConfig(permitsPerMinute = 1, burst = 0)
        }
    }

    @Test
    fun `rejects non-positive max entries`() {
        assertFailsWith<IllegalArgumentException> {
            IpRateLimitConfig(permitsPerMinute = 1, burst = 1, maxEntries = 0)
        }
    }
}
