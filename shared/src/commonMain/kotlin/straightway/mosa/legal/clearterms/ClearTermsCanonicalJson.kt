package straightway.mosa.legal.clearterms

class ClearTermsCanonicalJson {
    fun canonicalize(root: ClearTermsJsonNode.ObjectNode): String = renderObject(root)

    private fun renderObject(node: ClearTermsJsonNode.ObjectNode): String {
        val entries =
            node.entries.keys.sorted().map { key ->
                val value = node.entries.getValue(key)
                "${renderString(key)}:${renderValue(value)}"
            }
        return "{${entries.joinToString(",")}}"
    }

    private fun renderArray(node: ClearTermsJsonNode.ArrayNode): String {
        val items = node.items.joinToString(",") { item -> renderValue(item) }
        return "[$items]"
    }

    private fun renderValue(node: ClearTermsJsonNode): String =
        when (node) {
            is ClearTermsJsonNode.ObjectNode -> renderObject(node)
            is ClearTermsJsonNode.ArrayNode -> renderArray(node)
            is ClearTermsJsonNode.ScalarNode.StringNode -> renderString(node.value)
            is ClearTermsJsonNode.ScalarNode.NumberNode -> renderNumber(node.value)
            ClearTermsJsonNode.ScalarNode.NullNode -> "null"
        }

    private fun renderString(value: String): String = "\"${escapeString(value)}\""

    private fun renderNumber(value: Double): String {
        if (!value.isFinite()) {
            throw ClearTermsValidationException("Invalid number value")
        }
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }

    private fun escapeString(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
}
