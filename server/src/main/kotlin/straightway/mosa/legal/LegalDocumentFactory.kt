package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.document.Baseline
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter

internal class LegalDocumentFactory(
    private val parser: JsonParser,
    private val reader: DocumentReader,
    private val profileValidator: ProfileValidator,
    private val canonicalNodeRenderer: CanonicalNodeRenderer,
) {
    fun createDocuments(conversion: YamlCanonicalJsonConverter.ConversionResult): LegalDocuments =
        LegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            profileCanonicalJsons = conversion.profiles.map { it.canonicalJson },
            profileCanonicalJsonById = conversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson },
        )

    fun createFallbackDocuments(
        mandatoryConversion: YamlCanonicalJsonConverter.ConversionResult,
        kind: LegalDocumentKind,
        operatorValuesOverride: Map<String, JsonNode.ScalarNode> = emptyMap(),
    ): LegalDocuments {
        val fallbackCanonicalJson =
            synthesizedFallbackProfileCanonicalJson(
                mandatoryConversion = mandatoryConversion,
                operatorValuesOverride = operatorValuesOverride,
                kind = kind,
            )
        return LegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            profileCanonicalJsons = mandatoryConversion.profiles.map { it.canonicalJson } + fallbackCanonicalJson,
            profileCanonicalJsonById =
                mandatoryConversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson } +
                    (reader.readProfile(parser.parse(fallbackCanonicalJson)).id to fallbackCanonicalJson),
        )
    }

    fun extractOperatorValuesBestEffort(operatorYaml: String): Map<String, JsonNode.ScalarNode> {
        val root =
            try {
                yamlMapper.readTree(operatorYaml)
            } catch (_: Exception) {
                return emptyMap()
            }
        val valuesNode = root.path("overrides").path("values")
        if (!valuesNode.isObject) {
            return emptyMap()
        }
        val values = linkedMapOf<String, JsonNode.ScalarNode>()
        valuesNode.properties().forEach { (key, node) ->
            when {
                node.isTextual -> values[key] = JsonNode.ScalarNode.StringNode(node.asText())
                node.isNumber -> values[key] = JsonNode.ScalarNode.NumberNode(node.asDouble())
                node.isNull -> values[key] = JsonNode.ScalarNode.NullNode
            }
        }
        return values
    }

    private fun synthesizedFallbackProfileCanonicalJson(
        mandatoryConversion: YamlCanonicalJsonConverter.ConversionResult,
        operatorValuesOverride: Map<String, JsonNode.ScalarNode>,
        kind: LegalDocumentKind,
    ): String {
        val baseline = parseBaselineFrom(mandatoryConversion.baseline.canonicalJson)
        val mandatoryProfiles = mandatoryConversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) }
        val (effectiveTexts, mergedValues) = baseline.mergeWithProfiles(mandatoryProfiles, profileValidator)
        val fallbackValues =
            fallbackValues(
                effectiveTexts = effectiveTexts,
                mergedValues = mergedValues,
                operatorValuesOverride = operatorValuesOverride,
            )
        val textOverrides = textOverridesForUnresolvedBlocks(effectiveTexts)
        val profileNode =
            fallbackProfileNode(
                parentProfile = mandatoryProfiles.last(),
                parentHash = mandatoryConversion.profiles.last().hash,
                fallbackValues = fallbackValues,
                textOverrides = textOverrides,
                kind = kind,
            )
        return canonicalNodeRenderer.render(profileNode)
    }

    private fun fallbackValues(
        effectiveTexts: Map<String, List<TextBlock>>,
        mergedValues: Map<String, JsonNode.ScalarNode>,
        operatorValuesOverride: Map<String, JsonNode.ScalarNode>,
    ): Map<String, JsonNode.ScalarNode> {
        val mergedWithOperatorOverrides = linkedMapOf<String, JsonNode.ScalarNode>()
        mergedWithOperatorOverrides.putAll(mergedValues)
        mergedWithOperatorOverrides.putAll(operatorValuesOverride)
        val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
        val fallbackValues = linkedMapOf<String, JsonNode.ScalarNode>()
        placeholderTypes.forEach { (key, type) ->
            val existing = mergedWithOperatorOverrides[key]
            if (existing == null || existing is JsonNode.ScalarNode.NullNode) {
                fallbackValues[key] = defaultValueNode(type)
            } else if (operatorValuesOverride.containsKey(key)) {
                val coerced = coerceSingleValueOrNull(key, type, existing)
                if (coerced != null) {
                    fallbackValues[key] = coerced
                }
            }
        }
        return fallbackValues
    }

    private fun textOverridesForUnresolvedBlocks(effectiveTexts: Map<String, List<TextBlock>>): JsonNode.ObjectNode? {
        val unresolvedByLanguage = unresolvedBlockIdsByLanguage(effectiveTexts)
        if (unresolvedByLanguage.isEmpty()) {
            return null
        }
        return JsonNode.ObjectNode(
            unresolvedByLanguage.mapValues { (language, blockIds) ->
                val replace =
                    JsonNode.ObjectNode(
                        blockIds.associateWith { blockId ->
                            JsonNode.ScalarNode.StringNode(defaultReplacementText(language, blockId))
                        },
                    )
                JsonNode.ObjectNode(
                    mapOf(
                        "replace" to replace,
                    ),
                )
            },
        )
    }

    private fun unresolvedBlockIdsByLanguage(effectiveTexts: Map<String, List<TextBlock>>): Map<String, List<String>> =
        effectiveTexts
            .mapValues { (_, blocks) ->
                blocks.filter { block -> block.text == null }.map { block -> block.id }
            }.filterValues { ids -> ids.isNotEmpty() }

    private fun fallbackProfileNode(
        parentProfile: Profile,
        parentHash: String,
        fallbackValues: Map<String, JsonNode.ScalarNode>,
        textOverrides: JsonNode.ObjectNode?,
        kind: LegalDocumentKind,
    ): JsonNode.ObjectNode {
        val overridesEntries =
            linkedMapOf<String, JsonNode>().apply {
                this["values"] = JsonNode.ObjectNode(fallbackValues)
                if (textOverrides != null) {
                    this["texts"] = textOverrides
                }
            }
        return JsonNode.ObjectNode(
            linkedMapOf(
                "kind" to JsonNode.ScalarNode.StringNode("profile"),
                "id" to JsonNode.ScalarNode.StringNode(kind.fallbackProfileId),
                "version" to JsonNode.ScalarNode.StringNode("${parentProfile.version}-fallback"),
                "parent" to
                    JsonNode.ObjectNode(
                        linkedMapOf(
                            "id" to JsonNode.ScalarNode.StringNode(parentProfile.id),
                            "version" to JsonNode.ScalarNode.StringNode(parentProfile.version),
                            "hash" to JsonNode.ScalarNode.StringNode(parentHash),
                        ),
                    ),
                "overrides" to JsonNode.ObjectNode(overridesEntries),
            ),
        )
    }

    private fun defaultValueNode(type: PlaceholderType): JsonNode.ScalarNode =
        when (type) {
            PlaceholderType.STRING -> JsonNode.ScalarNode.StringNode("not specified")
            PlaceholderType.NUMBER -> JsonNode.ScalarNode.NumberNode(0.0)
            PlaceholderType.DURATION -> JsonNode.ScalarNode.StringNode("infinite")
            PlaceholderType.TIME -> JsonNode.ScalarNode.StringNode("*-*-*")
            PlaceholderType.WEEKDAY -> JsonNode.ScalarNode.StringNode("mon")
        }

    private fun defaultReplacementText(
        language: String,
        blockId: String,
    ): String = if (language.lowercase().startsWith("de")) "Nicht angegeben ($blockId)." else "Not specified ($blockId)."

    private fun coerceSingleValueOrNull(
        key: String,
        type: PlaceholderType,
        value: JsonNode.ScalarNode,
    ): JsonNode.ScalarNode? =
        try {
            profileValidator.validateAndCoerceValues(
                rawValues = mapOf(key to value),
                types = mapOf(key to type),
                requireAll = true,
            )
            value
        } catch (_: ValidationException) {
            null
        }

    private fun parseBaselineFrom(baselineCanonicalJson: String): Baseline = reader.readBaseline(parser.parse(baselineCanonicalJson))

    private companion object {
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
