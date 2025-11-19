package straightway.mosa.client.topic

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicFiltersTest {
    @Test
    fun `sanitizes blank entries`() {
        val sanitized =
            TopicFilters.sanitize(
                mapOf(
                    " " to "value",
                    "Topic" to "",
                    "Valid" to " identifier ",
                ),
            )

        assertEquals(mapOf("Valid" to "identifier"), sanitized)
    }
}
