package straightway.mosa.legal.clearterms.parse

import straightway.mosa.legal.clearterms.validation.ValidationException

class JsonObjectContract(
    private val map: JsonNode.ObjectNode,
) {
    fun requireNoExtraKeys(allowed: Set<String>) {
        val extra = map.entries.keys.firstOrNull { it !in allowed }
        if (extra != null) {
            throw ValidationException("Unknown key '$extra'")
        }
    }

    fun requireKeys(required: Set<String>) {
        val missing = required.firstOrNull { it !in map.entries.keys }
        if (missing != null) {
            throw ValidationException("Missing key '$missing'")
        }
    }

    fun requireString(key: String): String {
        val node = map.entries.getValue(key)
        val stringNode =
            node as? JsonNode.ScalarNode.StringNode
                ?: throw ValidationException("Key '$key' must be a string")
        return stringNode.value
    }

    fun requireMap(key: String): JsonNode.ObjectNode {
        val node = map.entries.getValue(key)
        return node as? JsonNode.ObjectNode
            ?: throw ValidationException("Key '$key' must be a mapping")
    }

    fun optionalNode(key: String): JsonNode? = map.entries[key]
}
