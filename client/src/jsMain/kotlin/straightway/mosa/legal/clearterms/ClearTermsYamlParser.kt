package straightway.mosa.legal.clearterms

class ClearTermsYamlParser {
    fun parse(yaml: String): ClearTermsYamlNode.MapNode {
        val options = js("({})").unsafeCast<YamlParseOptions>()
        options.uniqueKeys = true
        options.merge = false
        options.schema = "core"
        options.strict = true
        val document = parseDocument(yaml, options)
        if (document.errors.isNotEmpty()) {
            throw ClearTermsValidationException(document.errors.first().message)
        }
        if (document.warnings.isNotEmpty()) {
            throw ClearTermsValidationException(document.warnings.first().message)
        }
        val contents = document.contents ?: throw ClearTermsValidationException("Missing YAML content")
        rejectAnchorsAndAliases(contents)
        if (!isMap(contents)) {
            throw ClearTermsValidationException("Root YAML node must be a mapping")
        }
        return mapNode(contents.unsafeCast<YAMLMap>())
    }

    private fun rejectAnchorsAndAliases(node: YamlNode) {
        if (isAlias(node)) {
            throw ClearTermsValidationException("YAML aliases are not allowed")
        }
        val anchor = node.asDynamic().anchor as? String
        if (!anchor.isNullOrBlank()) {
            throw ClearTermsValidationException("YAML anchors are not allowed")
        }
        when {
            isMap(node) -> node.unsafeCast<YAMLMap>().items.forEach { pair ->
                rejectAnchorsAndAliases(pair.key)
                rejectAnchorsAndAliases(pair.value)
            }
            isSeq(node) -> node.unsafeCast<YAMLSeq>().items.forEach { item ->
                rejectAnchorsAndAliases(item)
            }
        }
    }

    private fun mapNode(node: YAMLMap): ClearTermsYamlNode.MapNode {
        val entries = linkedMapOf<String, ClearTermsYamlNode>()
        node.items.forEach { pair ->
            val keyNode = pair.key
            if (!isScalar(keyNode)) {
                throw ClearTermsValidationException("YAML mapping keys must be strings")
            }
            val scalar = keyNode.unsafeCast<YamlScalar>()
            val keyValue = scalar.value
            if (keyValue !is String) {
                throw ClearTermsValidationException("YAML mapping keys must be strings")
            }
            if (entries.containsKey(keyValue)) {
                throw ClearTermsValidationException("Duplicate key '$keyValue'")
            }
            entries[keyValue] = toNode(pair.value)
        }
        return ClearTermsYamlNode.MapNode(entries)
    }

    private fun toNode(node: YamlNode): ClearTermsYamlNode {
        if (isAlias(node)) {
            throw ClearTermsValidationException("YAML aliases are not allowed")
        }
        if (isMap(node)) {
            return mapNode(node.unsafeCast<YAMLMap>())
        }
        if (isSeq(node)) {
            val items = node.unsafeCast<YAMLSeq>().items.map { item -> toNode(item) }
            return ClearTermsYamlNode.SequenceNode(items)
        }
        if (isScalar(node)) {
            val value = node.unsafeCast<YamlScalar>().value
            return when (value) {
                null -> ClearTermsYamlNode.ScalarNode.NullNode
                is String -> ClearTermsYamlNode.ScalarNode.StringNode(value)
                is Number -> ClearTermsYamlNode.ScalarNode.NumberNode(value.toDouble())
                else -> throw ClearTermsValidationException("Unsupported scalar type")
            }
        }
        throw ClearTermsValidationException("Unsupported YAML node")
    }
}
