package straightway.mosa.legal.clearterms

class ClearTermsCanonicalYaml {
    fun canonicalize(root: ClearTermsYamlNode.MapNode): String = renderMap(root, 0).joinToString("\n")

    private fun renderMap(map: ClearTermsYamlNode.MapNode, indent: Int): List<String> {
        val prefix = " ".repeat(indent)
        val lines = mutableListOf<String>()
        map.entries.keys.sorted().forEach { key ->
            val value = map.entries.getValue(key)
            val renderedKey = renderString(key)
            when (value) {
                is ClearTermsYamlNode.MapNode -> {
                    lines += "$prefix$renderedKey:"
                    lines += renderMap(value, indent + 2)
                }
                is ClearTermsYamlNode.SequenceNode -> {
                    lines += "$prefix$renderedKey:"
                    lines += renderSequence(value, indent + 2)
                }
                is ClearTermsYamlNode.ScalarNode -> {
                    lines += "$prefix$renderedKey: ${renderScalar(value)}"
                }
            }
        }
        return lines
    }

    private fun renderSequence(sequence: ClearTermsYamlNode.SequenceNode, indent: Int): List<String> {
        val prefix = " ".repeat(indent)
        val lines = mutableListOf<String>()
        sequence.items.forEach { item ->
            when (item) {
                is ClearTermsYamlNode.MapNode -> {
                    lines += "$prefix-"
                    lines += renderMap(item, indent + 2)
                }
                is ClearTermsYamlNode.SequenceNode -> {
                    lines += "$prefix-"
                    lines += renderSequence(item, indent + 2)
                }
                is ClearTermsYamlNode.ScalarNode -> {
                    lines += "$prefix- ${renderScalar(item)}"
                }
            }
        }
        return lines
    }

    private fun renderScalar(node: ClearTermsYamlNode.ScalarNode): String = when (node) {
        is ClearTermsYamlNode.ScalarNode.StringNode -> renderString(node.value)
        is ClearTermsYamlNode.ScalarNode.NumberNode -> renderNumber(node.value)
        ClearTermsYamlNode.ScalarNode.NullNode -> "null"
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

    private fun escapeString(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
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
