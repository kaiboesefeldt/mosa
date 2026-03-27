package straightway.mosa.legal.clearterms.canonical

import straightway.mosa.legal.clearterms.parse.JsonNode

class CanonicalNodeRenderer(
    private val stringEscaper: CanonicalStringEscaper = CanonicalStringEscaper(),
    private val numberRenderer: CanonicalNumberRenderer = CanonicalNumberRenderer(),
) {
    fun render(node: JsonNode): String =
        when (node) {
            is JsonNode.ObjectNode -> renderObject(node)
            is JsonNode.ArrayNode -> renderArray(node)
            is JsonNode.ScalarNode.StringNode -> renderString(node)
            is JsonNode.ScalarNode.NumberNode -> renderNumber(node)
            is JsonNode.ScalarNode.BooleanNode -> renderBoolean(node)
            JsonNode.ScalarNode.NullNode -> "null"
        }

    private fun renderObject(node: JsonNode.ObjectNode): String {
        val entries =
            node.entries.keys.sorted().map { key ->
                val value = node.entries.getValue(key)
                "${renderString(key)}:${render(value)}"
            }
        return "{${entries.joinToString(",")}}"
    }

    private fun renderArray(node: JsonNode.ArrayNode): String {
        val items = node.items.joinToString(",") { item -> render(item) }
        return "[$items]"
    }

    private fun renderString(node: JsonNode.ScalarNode.StringNode): String = renderString(node.value)

    private fun renderString(value: String): String = "\"${stringEscaper.render(value)}\""

    private fun renderNumber(node: JsonNode.ScalarNode.NumberNode): String = numberRenderer.render(node.value)

    private fun renderBoolean(node: JsonNode.ScalarNode.BooleanNode): String = node.value.toString()
}
