package straightway.mosa.legal.clearterms

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

class ClearTermsYamlLanguageFragmentMerger(
    private val yamlMapper: ObjectMapper = defaultYamlMapper(),
) {
    fun merge(
        baseYaml: String,
        fragmentYamls: List<String>,
    ): String {
        if (fragmentYamls.isEmpty()) {
            return baseYaml
        }
        val base = parseObject(baseYaml, "Base document")
        val kind = base.requiredText("kind", "Base document")
        when (kind) {
            "baseline" -> mergeBaseline(base, fragmentYamls)
            "profile" -> mergeProfile(base, fragmentYamls)
            else -> throw ClearTermsValidationException("Language fragments are only supported for baseline and profile documents")
        }
        return yamlMapper.writeValueAsString(base)
    }

    private fun mergeBaseline(
        base: ObjectNode,
        fragmentYamls: List<String>,
    ) {
        val baseTexts = base.objectChildOrCreate("texts", "Base baseline")
        fragmentYamls.forEachIndexed { index, fragmentYaml ->
            val fragment = parseObject(fragmentYaml, "Fragment #${index + 1}")
            requireExactKeys(fragment, setOf("texts"), "Baseline fragment #${index + 1}")
            val fragmentTexts = fragment.requiredObject("texts", "Baseline fragment #${index + 1}")
            mergeLanguages(baseTexts, fragmentTexts)
        }
    }

    private fun mergeProfile(
        base: ObjectNode,
        fragmentYamls: List<String>,
    ) {
        val baseOverrides = base.objectChildOrCreate("overrides", "Base profile")
        val baseTexts = baseOverrides.objectChildOrCreate("texts", "Base profile overrides")
        fragmentYamls.forEachIndexed { index, fragmentYaml ->
            val fragment = parseObject(fragmentYaml, "Fragment #${index + 1}")
            requireExactKeys(fragment, setOf("overrides"), "Profile fragment #${index + 1}")
            val fragmentOverrides = fragment.requiredObject("overrides", "Profile fragment #${index + 1}")
            requireExactKeys(fragmentOverrides, setOf("texts"), "Profile fragment #${index + 1} overrides")
            val fragmentTexts = fragmentOverrides.requiredObject("texts", "Profile fragment #${index + 1} overrides")
            mergeLanguages(baseTexts, fragmentTexts)
        }
    }

    private fun mergeLanguages(
        targetTexts: ObjectNode,
        fragmentTexts: ObjectNode,
    ) {
        fragmentTexts.properties().forEach { (language, textsNode) ->
            if (targetTexts.has(language)) {
                throw ClearTermsValidationException("Language '$language' is defined more than once")
            }
            targetTexts.set<JsonNode>(language, textsNode.deepCopy())
        }
    }

    private fun parseObject(
        yaml: String,
        label: String,
    ): ObjectNode {
        val node =
            try {
                yamlMapper.readTree(yaml)
            } catch (exception: Exception) {
                throw ClearTermsValidationException("$label is not valid YAML: ${exception.message}")
            }
        return node as? ObjectNode
            ?: throw ClearTermsValidationException("$label root must be a mapping")
    }

    private fun ObjectNode.requiredObject(
        key: String,
        label: String,
    ): ObjectNode {
        val node = this[key] ?: throw ClearTermsValidationException("$label must contain '$key'")
        return node as? ObjectNode ?: throw ClearTermsValidationException("$label key '$key' must be a mapping")
    }

    private fun ObjectNode.requiredText(
        key: String,
        label: String,
    ): String {
        val node = this[key] ?: throw ClearTermsValidationException("$label must contain '$key'")
        return node.takeIf { it.isTextual }?.asText() ?: throw ClearTermsValidationException("$label key '$key' must be a string")
    }

    private fun ObjectNode.objectChildOrCreate(
        key: String,
        label: String,
    ): ObjectNode {
        val existing = this[key]
        if (existing == null || existing.isNull) {
            val created = yamlMapper.nodeFactory.objectNode()
            this.set<ObjectNode>(key, created)
            return created
        }
        return existing as? ObjectNode
            ?: throw ClearTermsValidationException("$label key '$key' must be a mapping")
    }

    private fun requireExactKeys(
        node: ObjectNode,
        expected: Set<String>,
        label: String,
    ) {
        val keys = node.fieldNames().asSequence().toSet()
        if (keys != expected) {
            val expectedKeys = expected.sorted().joinToString(", ")
            throw ClearTermsValidationException("$label must contain only: $expectedKeys")
        }
    }

    private companion object {
        private fun defaultYamlMapper(): ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
