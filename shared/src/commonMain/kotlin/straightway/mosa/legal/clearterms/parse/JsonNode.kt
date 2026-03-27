package straightway.mosa.legal.clearterms.parse

sealed class JsonNode {
    data class ObjectNode(
        val entries: Map<String, JsonNode>,
    ) : JsonNode()

    data class ArrayNode(
        val items: List<JsonNode>,
    ) : JsonNode()

    sealed class ScalarNode : JsonNode() {
        data class StringNode(
            val value: String,
        ) : ScalarNode()

        data class NumberNode(
            val value: Double,
        ) : ScalarNode()

        data class BooleanNode(
            val value: Boolean,
        ) : ScalarNode()

        object NullNode : ScalarNode()
    }
}
