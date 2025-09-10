package straightway.mosa.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import java.time.Instant

/**
 * Jackson serializers for Instant that use epoch seconds (Long) instead of milliseconds.
 * This ensures consistent representation across the entire application.
 */
object InstantJacksonSerializers {
    /**
     * Serializes Instant as epoch seconds (Long)
     */
    object InstantEpochSecondSerializer : JsonSerializer<Instant>() {
        override fun serialize(
            value: Instant,
            gen: JsonGenerator,
            serializers: SerializerProvider,
        ) {
            gen.writeNumber(value.epochSecond)
        }
    }

    /**
     * Deserializes epoch seconds (Long) to Instant
     */
    object InstantEpochSecondDeserializer : JsonDeserializer<Instant>() {
        override fun deserialize(
            parser: JsonParser,
            context: DeserializationContext,
        ): Instant = Instant.ofEpochSecond(parser.longValue)
    }

    /**
     * Creates a SimpleModule with both serializer and deserializer configured
     */
    fun createModule(): SimpleModule =
        SimpleModule().apply {
            addSerializer(Instant::class.java, InstantEpochSecondSerializer)
            addDeserializer(Instant::class.java, InstantEpochSecondDeserializer)
        }
}
