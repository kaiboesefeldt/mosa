package straightway.mosa.legal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import straightway.mosa.legal.clearterms.ClearTermsBaseline
import straightway.mosa.legal.clearterms.ClearTermsCanonicalJson
import straightway.mosa.legal.clearterms.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.ClearTermsPlaceholderType
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.ClearTermsProfileValidator
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter

class LegalService(
    private val canonicalJsonConverter: ClearTermsYamlCanonicalJsonConverter,
    private val operatorProfilePath: java.nio.file.Path,
    private val completenessChecker: ClearTermsProfileChainCompletenessChecker,
    private val parser: ClearTermsJsonParser,
    private val reader: ClearTermsDocumentReader,
    private val profileValidator: ClearTermsProfileValidator,
    private val canonicalJson: ClearTermsCanonicalJson,
) {
    private val documents: PrivacyLegalDocuments = loadPrivacyDocuments()

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    fun privacyServiceCanonicalJson(): String = documents.serviceCanonicalJson

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val baselineYaml = readRequiredResource(BASELINE_RESOURCE)
        val defaultYaml = readRequiredResource(DEFAULT_RESOURCE)
        val mosaYaml = readRequiredResource(MOSA_RESOURCE)
        val mandatoryConversion = canonicalJsonConverter.convert(baselineYaml, listOf(defaultYaml, mosaYaml))
        val mandatoryCanonicals = mandatoryConversion.profiles.map { artifact -> artifact.canonicalJson }
        val operatorYaml =
            readOptionalProfile(operatorProfilePath) ?: return fallbackToMosaServiceProfile(
                mandatoryConversion = mandatoryConversion,
                mandatoryCanonicals = mandatoryCanonicals,
                reason = "Operator profile file is missing at '${
                    operatorProfilePath.toAbsolutePath().normalize()
                }'",
            )

        val fullConversion =
            try {
                canonicalJsonConverter.convert(baselineYaml, listOf(defaultYaml, mosaYaml, operatorYaml))
            } catch (exception: ClearTermsValidationException) {
                return fallbackToMosaServiceProfile(
                    mandatoryConversion = mandatoryConversion,
                    mandatoryCanonicals = mandatoryCanonicals,
                    reason = "Operator profile is invalid and could not be converted: ${exception.message}",
                    operatorValuesOverride = extractOperatorValuesBestEffort(operatorYaml),
                )
            }

        val fullChainCanonicals = fullConversion.profiles.map { artifact -> artifact.canonicalJson }
        val analysis =
            completenessChecker.analyze(
                baselineCanonicalJson = fullConversion.baseline.canonicalJson,
                profileChainCanonicalJsons = fullChainCanonicals,
            )
        if (!analysis.complete) {
            return fallbackToMosaServiceProfile(
                mandatoryConversion = mandatoryConversion,
                mandatoryCanonicals = mandatoryCanonicals,
                reason = "Legal profile chain is incomplete",
                skeletonYamlOverride = analysis.skeletonYaml,
            )
        }
        val fullProfiles = fullConversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) }
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullConversion.baseline.canonicalJson,
                profiles = fullProfiles,
                requireAll = false,
            )
        valueTypeValidationError?.let {
            throw IllegalStateException(
                "Legal profile chain has invalid placeholder values: ${it.message}",
            )
        }
        val fullChainValidationError =
            validateChainValues(
                baselineCanonicalJson = fullConversion.baseline.canonicalJson,
                profiles = fullProfiles,
                requireAll = true,
            )

        if (fullChainValidationError != null) {
            val serviceProfileValues = fullProfiles.lastOrNull()?.overrides?.values ?: emptyMap()
            return fallbackToMosaServiceProfile(
                mandatoryConversion = mandatoryConversion,
                mandatoryCanonicals = mandatoryCanonicals,
                reason = "Legal profile chain has invalid placeholder values: ${fullChainValidationError.message}",
                operatorValuesOverride = serviceProfileValues,
            )
        }

        return PrivacyLegalDocuments(
            baselineCanonicalJson = fullConversion.baseline.canonicalJson,
            defaultCanonicalJson = fullConversion.profiles[0].canonicalJson,
            mosaCanonicalJson = fullConversion.profiles[1].canonicalJson,
            serviceCanonicalJson = fullConversion.profiles[2].canonicalJson,
        )
    }

    private fun readRequiredResource(path: String): String =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("Missing resource '$path'")

    private fun readOptionalProfile(path: java.nio.file.Path): String? {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!java.nio.file.Files
                .exists(normalizedPath)
        ) {
            return null
        }
        return java.nio.file.Files
            .readString(normalizedPath, Charsets.UTF_8)
    }

    private fun printProfileChainSkeleton(
        reason: String,
        skeletonYaml: String,
    ) {
        println("Legal profile chain completeness check failed: $reason")
        println("Suggested operator profile skeleton:")
        println(skeletonYaml)
    }

    private fun fallbackToMosaServiceProfile(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        mandatoryCanonicals: List<String>,
        reason: String,
        skeletonYamlOverride: String? = null,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode> = emptyMap(),
    ): PrivacyLegalDocuments {
        val analysis =
            completenessChecker.analyze(
                baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
                profileChainCanonicalJsons = mandatoryCanonicals,
            )
        printProfileChainSkeleton(
            reason = "$reason. Falling back to mosa service profile.",
            skeletonYaml = skeletonYamlOverride ?: analysis.skeletonYaml,
        )
        val fallbackServiceCanonicalJson =
            synthesizedFallbackServiceCanonicalJson(
                mandatoryConversion = mandatoryConversion,
                operatorValuesOverride = operatorValuesOverride,
            )
        return PrivacyLegalDocuments(
            baselineCanonicalJson = mandatoryConversion.baseline.canonicalJson,
            defaultCanonicalJson = mandatoryConversion.profiles[0].canonicalJson,
            mosaCanonicalJson = mandatoryConversion.profiles[1].canonicalJson,
            serviceCanonicalJson = fallbackServiceCanonicalJson,
        )
    }

    private fun synthesizedFallbackServiceCanonicalJson(
        mandatoryConversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
        operatorValuesOverride: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): String {
        val baseline = parseBaselineFrom(mandatoryConversion.baseline.canonicalJson)
        val mandatoryProfiles = mandatoryConversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) }
        var effectiveTexts = baseline.textsByLanguage
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        mandatoryProfiles.forEach { profile ->
            mergedValues.putAll(profile.overrides.values ?: emptyMap())
            effectiveTexts = profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
        }
        mergedValues.putAll(operatorValuesOverride)

        val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
        val fallbackValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        placeholderTypes.forEach { (key, type) ->
            val existing = mergedValues[key]
            if (existing == null || existing is ClearTermsJsonNode.ScalarNode.NullNode) {
                fallbackValues[key] = defaultValueNode(type)
            } else {
                val coerced = coerceSingleValueOrNull(key, type, existing)
                if (coerced != null && operatorValuesOverride.containsKey(key)) {
                    fallbackValues[key] = coerced
                }
            }
        }

        val unresolvedByLanguage =
            effectiveTexts
                .mapValues { (_, blocks) ->
                    blocks.filter { block -> block.text == null }.map { block -> block.id }
                }.filterValues { ids -> ids.isNotEmpty() }

        val textOverrides =
            if (unresolvedByLanguage.isEmpty()) {
                null
            } else {
                ClearTermsJsonNode.ObjectNode(
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

        val parentProfile = mandatoryProfiles.last()
        val parentHash = mandatoryConversion.profiles.last().hash
        val overridesEntries =
            linkedMapOf<String, ClearTermsJsonNode>().apply {
                this["values"] = ClearTermsJsonNode.ObjectNode(fallbackValues)
                if (textOverrides != null) {
                    this["texts"] = textOverrides
                }
            }
        val profileNode =
            ClearTermsJsonNode.ObjectNode(
                linkedMapOf(
                    "kind" to ClearTermsJsonNode.ScalarNode.StringNode("profile"),
                    "id" to ClearTermsJsonNode.ScalarNode.StringNode("mosa-privacy-fallback-service"),
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
        return canonicalJson.canonicalize(profileNode)
    }

    private fun validateChainValues(
        baselineCanonicalJson: String,
        profiles: List<ClearTermsProfile>,
        requireAll: Boolean,
    ): ClearTermsValidationException? =
        try {
            val baseline = parseBaselineFrom(baselineCanonicalJson)
            val (effectiveTexts, mergedValues) = baseline.mergeWithProfiles(profiles)
            profileValidator.validateFinalTextsResolved(effectiveTexts)
            val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
            profileValidator.validateAndCoerceValues(
                rawValues = mergedValues,
                types = placeholderTypes,
                requireAll = requireAll,
            )
            null
        } catch (exception: ClearTermsValidationException) {
            exception
        }

    private fun parseBaselineFrom(baselineCanonicalJson: String): ClearTermsBaseline =
        reader.readBaseline(parser.parse(baselineCanonicalJson))

    private data class MergeResult(
        val effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        val mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
    )

    private fun ClearTermsBaseline.mergeWithProfiles(profiles: List<ClearTermsProfile>): MergeResult {
        var effectiveTexts = textsByLanguage
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()
        profiles.forEach { profile ->
            mergedValues.putAll(profile.overrides.values ?: emptyMap())
            effectiveTexts =
                profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
        }
        return MergeResult(effectiveTexts, mergedValues)
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

    private fun extractOperatorValuesBestEffort(operatorYaml: String): Map<String, ClearTermsJsonNode.ScalarNode> {
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

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
        val serviceCanonicalJson: String,
    )

    private companion object {
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val DEFAULT_RESOURCE = "clearterms/privacy/privacy-gdpr-default.yaml"
        private const val MOSA_RESOURCE = "clearterms/privacy/mosa-privacy-gdpr-service.yaml"
        private val yamlMapper: ObjectMapper =
            ObjectMapper(YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
    }
}
