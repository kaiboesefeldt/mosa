package straightway.mosa.legal.clearterms

class ClearTermsJsonObjectContract(
    private val map: ClearTermsJsonNode.ObjectNode,
) {
    fun requireNoExtraKeys(allowed: Set<String>) {
        val extra = map.entries.keys.firstOrNull { it !in allowed }
        if (extra != null) {
            throw ClearTermsValidationException("Unknown key '$extra'")
        }
    }

    fun requireKeys(required: Set<String>) {
        val missing = required.firstOrNull { it !in map.entries.keys }
        if (missing != null) {
            throw ClearTermsValidationException("Missing key '$missing'")
        }
    }

    fun requireString(key: String): String {
        val node = map.entries.getValue(key)
        val stringNode =
            node as? ClearTermsJsonNode.ScalarNode.StringNode
                ?: throw ClearTermsValidationException("Key '$key' must be a string")
        return stringNode.value
    }

    fun requireMap(key: String): ClearTermsJsonNode.ObjectNode {
        val node = map.entries.getValue(key)
        return node as? ClearTermsJsonNode.ObjectNode
            ?: throw ClearTermsValidationException("Key '$key' must be a mapping")
    }

    fun optionalNode(key: String): ClearTermsJsonNode? = map.entries[key]
}
