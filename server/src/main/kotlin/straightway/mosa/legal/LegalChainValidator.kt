package straightway.mosa.legal

import straightway.mosa.legal.clearterms.document.Baseline
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.validation.ProfileChainCompletenessChecker
import straightway.mosa.legal.clearterms.validation.ProfileValidator
import straightway.mosa.legal.clearterms.validation.ValidationException
import java.nio.file.Path

internal class LegalChainValidator(
    private val completenessChecker: ProfileChainCompletenessChecker,
    private val parser: JsonParser,
    private val reader: DocumentReader,
    private val profileValidator: ProfileValidator,
    private val profileSource: LegalProfileSource,
) {
    fun createFullChainContext(
        conversion: straightway.mosa.legal.clearterms.yaml.YamlCanonicalJsonConverter.ConversionResult,
    ): FullChainContext =
        FullChainContext(
            conversion = conversion,
            profiles = conversion.profiles.map { artifact -> reader.readProfile(parser.parse(artifact.canonicalJson)) },
            profileCanonicalJsons = conversion.profiles.map { artifact -> artifact.canonicalJson },
        )

    fun validateOperatorProfileForStartup(
        kind: LegalDocumentKind,
        profilePath: Path,
        mandatoryContext: MandatoryChainContext,
    ) {
        val normalizedPath = profilePath.toAbsolutePath().normalize()
        val operatorYaml =
            profileSource.readOptionalProfile(profilePath)
                ?: throw IllegalStateException(
                    startupValidationMessage(
                        kind = kind,
                        operatorMetadata = OperatorProfileMetadata.missing(normalizedPath.toString()),
                        reason = "operator profile file is missing at '$normalizedPath'",
                        availableVersions = mandatoryContext.availableProfileVersionsText(),
                    ),
                )
        val fullConversion =
            try {
                profileSource.convertFullChain(
                    mandatoryContext = mandatoryContext,
                    operatorYaml = operatorYaml,
                )
            } catch (exception: ValidationException) {
                throw IllegalStateException(
                    startupValidationMessage(
                        kind = kind,
                        operatorMetadata = profileSource.parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                        reason =
                            "operator profile at '$normalizedPath' " +
                                "is invalid and could not be converted: ${exception.message}",
                        availableVersions = mandatoryContext.availableProfileVersionsText(),
                    ),
                )
            }
        val fullChainContext = createFullChainContext(fullConversion)
        val analysis =
            analyzeChainCompleteness(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profileCanonicalJsons = fullChainContext.profileCanonicalJsons,
            )
        if (!analysis.complete) {
            val incompleteDetails = incompleteChainDetails(analysis)
            throw IllegalStateException(
                startupValidationMessage(
                    kind = kind,
                    operatorMetadata = profileSource.parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                    reason = "legal profile chain is incomplete for '$normalizedPath'$incompleteDetails",
                    availableVersions = mandatoryContext.availableProfileVersionsText(),
                ),
            )
        }
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        check(valueTypeValidationError == null) {
            startupValidationMessage(
                kind = kind,
                operatorMetadata = profileSource.parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                reason =
                    "legal profile chain has invalid placeholder values " +
                        "in '$normalizedPath': ${valueTypeValidationError?.message}",
                availableVersions = mandatoryContext.availableProfileVersionsText(),
            )
        }
        val missingRequiredValuesError = missingRequiredChainValuesError(fullChainContext)
        check(missingRequiredValuesError == null) {
            startupValidationMessage(
                kind = kind,
                operatorMetadata = profileSource.parseOperatorProfileMetadata(operatorYaml, normalizedPath),
                reason =
                    "legal profile chain has invalid placeholder values " +
                        "in '$normalizedPath': ${missingRequiredValuesError?.message}",
                availableVersions = mandatoryContext.availableProfileVersionsText(),
            )
        }
    }

    fun requireValidFullChain(
        fullChainContext: FullChainContext,
        affectedProfilePath: Path,
    ) {
        val analysis = analyzeChainCompleteness(fullChainContext.conversion.baseline.canonicalJson, fullChainContext.profileCanonicalJsons)
        if (!analysis.complete) {
            throw IllegalStateException(
                "Legal profile chain is incomplete for '${affectedProfilePath.toAbsolutePath().normalize()}'",
            )
        }
        val valueTypeValidationError =
            validateChainValues(
                baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
                profiles = fullChainContext.profiles,
                requireAll = false,
            )
        check(valueTypeValidationError == null) {
            "Legal profile chain has invalid placeholder values: ${valueTypeValidationError?.message}"
        }
        val fullChainValidationError = missingRequiredChainValuesError(fullChainContext) ?: return
        throw IllegalStateException(
            "Legal profile chain has invalid placeholder values in " +
                "'${affectedProfilePath.toAbsolutePath().normalize()}': ${fullChainValidationError.message}",
        )
    }

    private fun startupValidationMessage(
        kind: LegalDocumentKind,
        operatorMetadata: OperatorProfileMetadata,
        reason: String,
        availableVersions: String,
    ): String =
        "Legal operator profile is missing or incomplete: " +
            "documentKind=${kind.documentKind}; " +
            "targetOperatorProfile=${operatorMetadata.id}@${operatorMetadata.version}; " +
            "referencedParent=${operatorMetadata.referencedParentId}@${operatorMetadata.referencedParentVersion}; " +
            "reason=$reason; " +
            "availableVersions=$availableVersions"

    private fun incompleteChainDetails(analysis: ProfileChainCompletenessChecker.Result): String {
        val details = mutableListOf<String>()
        if (analysis.missingPlaceholderKeys.isNotEmpty()) {
            details += "missing placeholder keys: ${analysis.missingPlaceholderKeys.joinToString(", ")}"
        }
        val unresolvedBlocks =
            analysis.unresolvedRequiredBlockIdsByLanguage
                .flatMap { (language, blockIds) -> blockIds.map { blockId -> "$language:$blockId" } }
        if (unresolvedBlocks.isNotEmpty()) {
            details += "unresolved block ids: ${unresolvedBlocks.joinToString(", ")}"
        }
        return if (details.isEmpty()) "" else " (${details.joinToString("; ")})"
    }

    private fun missingRequiredChainValuesError(fullChainContext: FullChainContext): ValidationException? =
        validateChainValues(
            baselineCanonicalJson = fullChainContext.conversion.baseline.canonicalJson,
            profiles = fullChainContext.profiles,
            requireAll = true,
        )

    private fun analyzeChainCompleteness(
        baselineCanonicalJson: String,
        profileCanonicalJsons: List<String>,
    ): ProfileChainCompletenessChecker.Result =
        completenessChecker.analyze(
            baselineCanonicalJson = baselineCanonicalJson,
            profileChainCanonicalJsons = profileCanonicalJsons,
        )

    private fun validateChainValues(
        baselineCanonicalJson: String,
        profiles: List<Profile>,
        requireAll: Boolean,
    ): ValidationException? =
        try {
            val baseline = parseBaselineFrom(baselineCanonicalJson)
            val (effectiveTexts, mergedValues) = baseline.mergeWithProfiles(profiles, profileValidator)
            profileValidator.validateFinalTextsResolved(effectiveTexts)
            val placeholderTypes = profileValidator.placeholdersByKey(effectiveTexts)
            profileValidator.validateAndCoerceValues(
                rawValues = mergedValues,
                types = placeholderTypes,
                requireAll = requireAll,
            )
            null
        } catch (exception: ValidationException) {
            exception
        }

    private fun parseBaselineFrom(baselineCanonicalJson: String): Baseline = reader.readBaseline(parser.parse(baselineCanonicalJson))
}

internal fun Baseline.mergeWithProfiles(
    profiles: List<Profile>,
    profileValidator: ProfileValidator,
): MergeResult {
    var effectiveTexts = textsByLanguage
    val mergedValues = linkedMapOf<String, straightway.mosa.legal.clearterms.parse.JsonNode.ScalarNode>()
    profiles.forEach { profile ->
        mergedValues.putAll(profile.overrides.values ?: emptyMap())
        effectiveTexts =
            profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
    }
    return MergeResult(effectiveTexts, mergedValues)
}
