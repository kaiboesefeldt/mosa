package straightway.mosa.legal.clearterms

class ClearTermsDocumentReader {
    fun readBaseline(root: ClearTermsYamlNode.MapNode): ClearTermsBaseline {
        requireNoExtraKeys(root, setOf("kind", "id", "version", "texts"))
        requireKeys(root, setOf("kind", "id", "version", "texts"))
        val kind = requireString(root, "kind")
        if (kind != "baseline") {
            throw ClearTermsValidationException("Invalid baseline kind")
        }
        val id = requireString(root, "id")
        val version = requireString(root, "version")
        val textsNode = requireMap(root, "texts")
        if (textsNode.entries.isEmpty()) {
            throw ClearTermsValidationException("Baseline must define at least one language")
        }
        val textsByLanguage = textsNode.entries.mapValues { (language, node) ->
            val blocksNode = node as? ClearTermsYamlNode.SequenceNode
                ?: throw ClearTermsValidationException("Texts for '$language' must be a list")
            blocksNode.items.map { item ->
                val blockMap = item as? ClearTermsYamlNode.MapNode
                    ?: throw ClearTermsValidationException("Text block must be a mapping")
                requireNoExtraKeys(blockMap, setOf("id", "text"))
                requireKeys(blockMap, setOf("id", "text"))
                val blockId = requireString(blockMap, "id")
                val blockText = requireString(blockMap, "text")
                ClearTermsTextBlock(blockId, blockText)
            }
        }
        return ClearTermsBaseline(id, version, textsByLanguage)
    }

    fun readDefault(root: ClearTermsYamlNode.MapNode): ClearTermsDefaultProfile {
        requireNoExtraKeys(root, setOf("kind", "id", "version", "baseline", "values"))
        requireKeys(root, setOf("kind", "id", "version", "baseline", "values"))
        val kind = requireString(root, "kind")
        if (kind != "default") {
            throw ClearTermsValidationException("Invalid default kind")
        }
        val id = requireString(root, "id")
        val version = requireString(root, "version")
        val baselineMap = requireMap(root, "baseline")
        requireNoExtraKeys(baselineMap, setOf("id", "version", "hash"))
        requireKeys(baselineMap, setOf("id", "version", "hash"))
        val baselineReference = ClearTermsReference(
            id = requireString(baselineMap, "id"),
            version = requireString(baselineMap, "version"),
            hash = requireString(baselineMap, "hash"),
        )
        val valuesMap = requireMap(root, "values")
        val values = valuesMap.entries.mapValues { (_, node) ->
            node as? ClearTermsYamlNode.ScalarNode
                ?: throw ClearTermsValidationException("Values must be scalar")
        }
        return ClearTermsDefaultProfile(id, version, baselineReference, values)
    }

    fun readDeviation(root: ClearTermsYamlNode.MapNode): ClearTermsDeviationProfile {
        requireNoExtraKeys(root, setOf("kind", "id", "version", "default", "overrides"))
        requireKeys(root, setOf("kind", "id", "version", "default", "overrides"))
        val kind = requireString(root, "kind")
        if (kind != "deviation") {
            throw ClearTermsValidationException("Invalid deviation kind")
        }
        val id = requireString(root, "id")
        val version = requireString(root, "version")
        val defaultMap = requireMap(root, "default")
        requireNoExtraKeys(defaultMap, setOf("id", "version", "hash"))
        requireKeys(defaultMap, setOf("id", "version", "hash"))
        val defaultReference = ClearTermsReference(
            id = requireString(defaultMap, "id"),
            version = requireString(defaultMap, "version"),
            hash = requireString(defaultMap, "hash"),
        )
        val overridesMap = requireMap(root, "overrides")
        requireNoExtraKeys(overridesMap, setOf("values", "texts"))
        requireKeys(overridesMap, setOf("values"))
        val valuesMap = requireMap(overridesMap, "values")
        val values = valuesMap.entries.mapValues { (_, node) ->
            node as? ClearTermsYamlNode.ScalarNode
                ?: throw ClearTermsValidationException("Override values must be scalar")
        }
        val textsNode = overridesMap.entries["texts"]
        val texts = textsNode?.let { parseTextOverrides(it) }
        val overrides = ClearTermsDeviationProfile.Overrides(values, texts)
        return ClearTermsDeviationProfile(id, version, defaultReference, overrides)
    }

    private fun parseTextOverrides(node: ClearTermsYamlNode): Map<String, ClearTermsDeviationProfile.TextOverrides> {
        val map = node as? ClearTermsYamlNode.MapNode
            ?: throw ClearTermsValidationException("Overrides texts must be a mapping")
        return map.entries.mapValues { (language, entryNode) ->
            val languageMap = entryNode as? ClearTermsYamlNode.MapNode
                ?: throw ClearTermsValidationException("Overrides for '$language' must be a mapping")
            requireNoExtraKeys(languageMap, setOf("replace", "append"))
            requireKeys(languageMap, setOf("replace", "append"))
            val replaceNode = requireMap(languageMap, "replace")
            val replace = replaceNode.entries.mapValues { (_, replaceValue) ->
                val textNode = replaceValue as? ClearTermsYamlNode.ScalarNode.StringNode
                    ?: throw ClearTermsValidationException("Replace text must be string")
                textNode.value
            }
            val appendNode = requireSequence(languageMap, "append")
            val append = appendNode.items.map { item ->
                val blockMap = item as? ClearTermsYamlNode.MapNode
                    ?: throw ClearTermsValidationException("Append text must be a mapping")
                requireNoExtraKeys(blockMap, setOf("id", "text"))
                requireKeys(blockMap, setOf("id", "text"))
                val blockId = requireString(blockMap, "id")
                val blockText = requireString(blockMap, "text")
                ClearTermsTextBlock(blockId, blockText)
            }
            ClearTermsDeviationProfile.TextOverrides(replace, append)
        }
    }

    private fun requireNoExtraKeys(map: ClearTermsYamlNode.MapNode, allowed: Set<String>) {
        val extra = map.entries.keys.firstOrNull { it !in allowed }
        if (extra != null) {
            throw ClearTermsValidationException("Unknown key '$extra'")
        }
    }

    private fun requireKeys(map: ClearTermsYamlNode.MapNode, required: Set<String>) {
        val missing = required.firstOrNull { it !in map.entries.keys }
        if (missing != null) {
            throw ClearTermsValidationException("Missing key '$missing'")
        }
    }

    private fun requireString(map: ClearTermsYamlNode.MapNode, key: String): String {
        val node = map.entries[key] ?: throw ClearTermsValidationException("Missing key '$key'")
        val stringNode = node as? ClearTermsYamlNode.ScalarNode.StringNode
            ?: throw ClearTermsValidationException("Key '$key' must be a string")
        return stringNode.value
    }

    private fun requireMap(map: ClearTermsYamlNode.MapNode, key: String): ClearTermsYamlNode.MapNode {
        val node = map.entries[key] ?: throw ClearTermsValidationException("Missing key '$key'")
        return node as? ClearTermsYamlNode.MapNode
            ?: throw ClearTermsValidationException("Key '$key' must be a mapping")
    }

    private fun requireSequence(map: ClearTermsYamlNode.MapNode, key: String): ClearTermsYamlNode.SequenceNode {
        val node = map.entries[key] ?: throw ClearTermsValidationException("Missing key '$key'")
        return node as? ClearTermsYamlNode.SequenceNode
            ?: throw ClearTermsValidationException("Key '$key' must be a list")
    }
}
