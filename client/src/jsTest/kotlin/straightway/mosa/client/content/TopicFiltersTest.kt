package straightway.mosa.client.content

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicFiltersTest {
    @Test
    fun `sanitizes invalid entries`() {
        val sanitized =
            TopicFilters.sanitize(
                mapOf(
                    " " to 0.7f,
                    "Topic" to Float.NaN,
                    "Valid" to 0.8f,
                ),
            )

        assertEquals(mapOf("Valid" to 0.8f), sanitized)
    }
}
