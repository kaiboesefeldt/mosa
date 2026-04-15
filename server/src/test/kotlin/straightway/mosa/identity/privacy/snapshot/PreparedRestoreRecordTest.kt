package straightway.mosa.identity.privacy.snapshot

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PreparedRestoreRecordTest {
    @Test
    fun `equals returns true for the same instance`() {
        val record = record()

        assertTrue(record == record)
    }

    @Test
    fun `equals returns false for null and different type`() {
        val record = record()

        assertFalse(record.equals(null))
        assertFalse(record.equals("different"))
    }

    @Test
    fun `equals returns false for different id`() {
        assertNotEquals(record(id = "first"), record(id = "second"))
    }

    @Test
    fun `equals returns false for different text`() {
        assertNotEquals(record(text = "first"), record(text = "second"))
    }

    @Test
    fun `equals returns false for different fields`() {
        assertNotEquals(
            record(fields = mapOf("authorGID" to "gid-1")),
            record(fields = mapOf("authorGID" to "gid-2")),
        )
    }

    @Test
    fun `equals returns false for different vector contents`() {
        assertNotEquals(record(vector = floatArrayOf(1f, 2f)), record(vector = floatArrayOf(1f, 3f)))
    }

    @Test
    fun `equals and hash code match for identical content`() {
        val first = record()
        val second = record()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    private fun record(
        id: String = "record-1",
        text: String = "Text",
        fields: Map<String, String> = mapOf("authorGID" to "gid-1", "receivedAt" to "100"),
        vector: FloatArray = floatArrayOf(1f, 2f),
    ) = PreparedRestoreRecord(
        id = id,
        text = text,
        fields = fields,
        vector = vector,
    )
}
