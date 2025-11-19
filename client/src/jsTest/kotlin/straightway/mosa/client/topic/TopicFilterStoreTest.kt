package straightway.mosa.client.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicFilterStoreTest {
    @Test
    fun `sanitizes blank entries`() {
        val sanitized =
            TopicFilterPayload.sanitize(
                mapOf(
                    " " to "value",
                    "Topic" to "",
                    "Valid" to " identifier ",
                )
            )

        assertEquals(mapOf("Valid" to "identifier"), sanitized)
    }

    @Test
    fun `encodes and parses filters`() {
        val filters = mapOf("Topic A" to "id-1", "Topic B" to "id-2")

        val encoded = TopicFilterPayload.encode(filters)
        val decoded = TopicFilterPayload.parse(encoded)

        assertEquals(filters, decoded)
    }

    @Test
    fun `parse returns empty for invalid payload`() {
        val parsed = TopicFilterPayload.parse("???")

        assertTrue(parsed.isEmpty())
    }
}
