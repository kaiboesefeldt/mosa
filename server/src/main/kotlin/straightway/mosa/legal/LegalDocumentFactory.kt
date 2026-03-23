package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import straightway.mosa.legal.clearterms.ClearTermsBaseline
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.canonical.ClearTermsCanonicalNodeRenderer
import straightway.mosa.legal.clearterms.parse.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderType
import straightway.mosa.legal.clearterms.validation.ClearTermsProfileValidator

internal class LegalDocumentFactory(
    private val parser: ClearTermsJsonParser,
    private val reader: ClearTermsDocumentReader,
    private val profileValidator: ClearTermsProfileValidator,
    private val canonicalNodeRenderer: ClearTermsCanonicalNodeRenderer,
) {
    fun createDocuments(conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult): LegalDocuments =
        LegalDocuments(
            baselineCanonicalJson = conversion.baseline.canonicalJson,
            profileCanonicalJsons = conversion.profiles.map { it.canonicalJson },
            profileCanonicalJsonById = conversion.profiles.associate { artifact -> artifact.profileId() to artifact.canonicalJson },
        )

    fun createFallbackDocuments(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        kind: LegalDocumentKind,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode> = emptyMap(),
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

    fun extractOperatorValuesBestEffort(operatorYaml: String): Map<String, ClearTermsJsonNode.ScalarNode> {
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
        val values = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        valuesNode.properties().forEach { (key, node) ->
            when {
                node.isTextual -> values[key] = ClearTermsJsonNode.ScalarNode.StringNode(node.asText())
                node.isNumber -> values[key] = ClearTermsJsonNode.ScalarNode.NumberNode(node.asDouble())
                node.isNull -> values[key] = ClearTermsJsonNode.ScalarNode.NullNode
            }
        }
        return values
    }

    private fun synthesizedFallbackProfileCanonicalJson(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
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
        effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): Map<String, ClearTermsJsonNode.ScalarNode> {
        val mergedWithOperatorOverrides = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        mergedWithOperatorOverrides.putAll(mergedValues)
        mergedWithOperatorOverrides.putAll(operatorValuesOverride)
        val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
        val fallbackValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        placeholderTypes.forEach { (key, type) ->
            val existing = mergedWithOperatorOverrides[key]
            if (existing == null || existing is ClearTermsJsonNode.ScalarNode.NullNode) {
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

    private fun textOverridesForUnresolvedBlocks(effectiveTexts: Map<String, List<ClearTermsTextBlock>>): ClearTermsJsonNode.ObjectNode? {
        val unresolvedByLanguage = unresolvedBlockIdsByLanguage(effectiveTexts)
        if (unresolvedByLanguage.isEmpty()) {
            return null
        }
        return ClearTermsJsonNode.ObjectNode(
            unresolvedByLanguage.mapValues { (language, blockIds) ->
                val replace =
                    ClearTermsJsonNode.ObjectNode(
                        blockIds.associateWith { blockId ->
                            ClearTermsJsonNode.ScalarNode.StringNode(defaultReplacementText(language, blockId))
                        },
                    )
                ClearTermsJsonNode.ObjectNode(
                    mapOf(
                        "replace" to replace,
                    ),
                )
            },
        )
    }

    private fun unresolvedBlockIdsByLanguage(effectiveTexts: Map<String, List<ClearTermsTextBlock>>): Map<String, List<String>> =
        effectiveTexts
            .mapValues { (_, blocks) ->
                blocks.filter { block -> block.text == null }.map { block -> block.id }
            }.filterValues { ids -> ids.isNotEmpty() }

    private fun fallbackProfileNode(
        parentProfile: ClearTermsProfile,
        parentHash: String,
        fallbackValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        textOverrides: ClearTermsJsonNode.ObjectNode?,
        kind: LegalDocumentKind,
    ): ClearTermsJsonNode.ObjectNode {
        val overridesEntries =
            linkedMapOf<String, ClearTermsJsonNode>().apply {
                this["values"] = ClearTermsJsonNode.ObjectNode(fallbackValues)
                if (textOverrides != null) {
                    this["texts"] = textOverrides
                }
            }
        return ClearTermsJsonNode.ObjectNode(
            linkedMapOf(
                "kind" to ClearTermsJsonNode.ScalarNode.StringNode("profile"),
                "id" to ClearTermsJsonNode.ScalarNode.StringNode(kind.fallbackProfileId),
                "version" to ClearTermsJsonNode.ScalarNode.StringNode("${parentProfile.version}-fallback"),
                "parent" to
                    ClearTermsJsonNode.ObjectNode(
                        linkedMapOf(
                            "id" to ClearTermsJsonNode.ScalarNode.StringNode(parentProfile.id),
                            "version" to ClearTermsJsonNode.ScalarNode.StringNode(parentProfile.version),
                            "hash" to ClearTermsJsonNode.ScalarNode.StringNode(parentHash),
                        ),
                    ),
                "overrides" to ClearTermsJsonNode.ObjectNode(overridesEntries),
            ),
        )
    }

    private fun defaultValueNode(type: ClearTermsPlaceholderType): ClearTermsJsonNode.ScalarNode =
        when (type) {
            ClearTermsPlaceholderType.STRING -> ClearTermsJsonNode.ScalarNode.StringNode("not specified")
            ClearTermsPlaceholderType.NUMBER -> ClearTermsJsonNode.ScalarNode.NumberNode(0.0)
            ClearTermsPlaceholderType.DURATION -> ClearTermsJsonNode.ScalarNode.StringNode("infinite")
            ClearTermsPlaceholderType.TIME -> ClearTermsJsonNode.ScalarNode.StringNode("*-*-*")
            ClearTermsPlaceholderType.WEEKDAY -> ClearTermsJsonNode.ScalarNode.StringNode("mon")
        }

    private fun defaultReplacementText(
        language: String,
        blockId: String,
    ): String = if (language.lowercase().startsWith("de")) "Nicht angegeben ($blockId)." else "Not specified ($blockId)."

    private fun coerceSingleValueOrNull(
        key: String,
        type: ClearTermsPlaceholderType,
        value: ClearTermsJsonNode.ScalarNode,
    ): ClearTermsJsonNode.ScalarNode? =
        try {
            profileValidator.validateAndCoerceValues(
                rawValues = mapOf(key to value),
                types = mapOf(key to type),
                requireAll = true,
            )
            value
        } catch (_: ClearTermsValidationException) {
            null
        }

    private fun parseBaselineFrom(baselineCanonicalJson: String): ClearTermsBaseline =
        reader.readBaseline(parser.parse(baselineCanonicalJson))

    private companion object {
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
