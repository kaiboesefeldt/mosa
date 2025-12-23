package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeWindowTest {
    @Test
    fun `stores count and total time`() {
        val window = TimeWindow(count = 3L, totalTimeMs = 12.5)

        assertEquals(3L, window.count)
        assertEquals(12.5, window.totalTimeMs)
    }
}
