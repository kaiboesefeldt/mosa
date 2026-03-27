package straightway.mosa.legal.clearterms.parse

import straightway.mosa.legal.clearterms.validation.ValidationException

class JsonParser {
    fun parse(json: String): JsonNode.ObjectNode {
        val reader = JsonValueReader(json)
        val value = reader.parseValue()
        reader.skipWhitespace()
        if (reader.hasRemaining()) {
            throw ValidationException("Trailing content after JSON value")
        }
        return value as? JsonNode.ObjectNode
            ?: throw ValidationException("Root JSON value must be an object")
    }
}
