package straightway.mosa.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import straightway.mosa.jackson.InstantJacksonSerializers.createModule
import java.time.Instant
import kotlin.test.assertEquals

class InstantJacksonSerializersTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(createModule())
        }

    @Test
    fun `serializes Instant to epoch seconds`() {
        val instant = Instant.ofEpochSecond(1609459200L) // 2021-01-01T00:00:00Z
        val json = objectMapper.writeValueAsString(instant)

        assertEquals("1609459200", json)
    }

    @Test
    fun `serializes zero epoch to zero`() {
        val instant = Instant.ofEpochSecond(0L) // 1970-01-01T00:00:00Z
        val json = objectMapper.writeValueAsString(instant)

        assertEquals("0", json)
    }

    @Test
    fun `serializes negative epoch seconds`() {
        val instant = Instant.ofEpochSecond(-86400L) // 1969-12-31T00:00:00Z
        val json = objectMapper.writeValueAsString(instant)

        assertEquals("-86400", json)
    }

    @Test
    fun `deserializes epoch seconds to Instant`() {
        val json = "1609459200"
        val instant = objectMapper.readValue(json, Instant::class.java)

        assertEquals(Instant.ofEpochSecond(1609459200L), instant)
    }

    @Test
    fun `deserializes zero to epoch zero`() {
        val json = "0"
        val instant = objectMapper.readValue(json, Instant::class.java)

        assertEquals(Instant.ofEpochSecond(0L), instant)
    }

    @Test
    fun `deserializes negative epoch seconds to Instant`() {
        val json = "-86400"
        val instant = objectMapper.readValue(json, Instant::class.java)

        assertEquals(Instant.ofEpochSecond(-86400L), instant)
    }

    @Test
    fun `round trip serialization preserves instant`() {
        val original = Instant.ofEpochSecond(1234567890L)
        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(json, Instant::class.java)

        assertEquals(original, deserialized)
    }

    @Test
    fun `ignores nanoseconds precision in serialization`() {
        val instantWithNanos = Instant.ofEpochSecond(1609459200L, 500_000_000L) // with 0.5 seconds
        val json = objectMapper.writeValueAsString(instantWithNanos)

        assertEquals("1609459200", json) // Nanoseconds are ignored
    }
}
