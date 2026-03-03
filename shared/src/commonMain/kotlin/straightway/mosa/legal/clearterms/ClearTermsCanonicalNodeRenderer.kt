package straightway.mosa.legal.clearterms

class ClearTermsCanonicalNodeRenderer(
    private val stringEscaper: ClearTermsCanonicalStringEscaper = ClearTermsCanonicalStringEscaper(),
    private val numberRenderer: ClearTermsCanonicalNumberRenderer = ClearTermsCanonicalNumberRenderer(),
) {
    fun render(node: ClearTermsJsonNode): String =
        when (node) {
            is ClearTermsJsonNode.ObjectNode -> renderObject(node)
            is ClearTermsJsonNode.ArrayNode -> renderArray(node)
            is ClearTermsJsonNode.ScalarNode.StringNode -> renderString(node.value)
            is ClearTermsJsonNode.ScalarNode.NumberNode -> numberRenderer.render(node.value)
            is ClearTermsJsonNode.ScalarNode.BooleanNode -> node.value.toString()
            ClearTermsJsonNode.ScalarNode.NullNode -> "null"
        }

    private fun renderObject(node: ClearTermsJsonNode.ObjectNode): String {
        val entries =
            node.entries.keys.sorted().map { key ->
                val value = node.entries.getValue(key)
                "${renderString(key)}:${render(value)}"
            }
        return "{${entries.joinToString(",")}}"
    }

    private fun renderArray(node: ClearTermsJsonNode.ArrayNode): String {
        val items = node.items.joinToString(",") { item -> render(item) }
        return "[$items]"
    }

    private fun renderString(value: String): String = "\"${stringEscaper.render(value)}\""
}
