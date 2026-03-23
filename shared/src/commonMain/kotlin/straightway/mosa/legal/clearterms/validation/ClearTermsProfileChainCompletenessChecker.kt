package straightway.mosa.legal.clearterms.validation

import straightway.mosa.legal.clearterms.ClearTermsBaseline
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.parse.ClearTermsDocumentReader
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholder
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderType

class ClearTermsProfileChainCompletenessChecker(
    private val parser: ClearTermsJsonParser = ClearTermsJsonParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val profileValidator: ClearTermsProfileValidator =
        ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
) {
    data class Result(
        val complete: Boolean,
        val missingPlaceholderKeys: List<String>,
        val unresolvedRequiredBlockIdsByLanguage: Map<String, List<String>>,
        val skeletonYaml: String,
    )

    fun analyze(
        baselineCanonicalJson: String,
        profileChainCanonicalJsons: List<String>,
    ): Result {
        val baseline = reader.readBaseline(parser.parse(baselineCanonicalJson))
        profileValidator.validateBaselineStructure(baseline)
        val requiredBlockIds = requiredBlockIds(baseline)
        val profileChain = profileChainCanonicalJsons.map { reader.readProfile(parser.parse(it)) }
        val chainState = applyProfileChain(baseline, profileChain)
        val unresolvedRequiredBlockIdsByLanguage = unresolvedRequiredBlocks(chainState.effectiveTexts, requiredBlockIds)
        val missingPlaceholdersByKey =
            missingPlaceholdersByKey(
                textsByLanguage = chainState.effectiveTexts,
                mergedValues = chainState.mergedValues,
            )
        val missingPlaceholderKeys = missingPlaceholdersByKey.keys.toList()
        val complete = isComplete(unresolvedRequiredBlockIdsByLanguage, missingPlaceholderKeys)
        val parentReference = parentReference(baseline, profileChain)
        val skeletonYaml =
            skeletonYaml(
                parentId = parentReference.id,
                parentVersion = parentReference.version,
                requiredBlocksByLanguage = requiredBlocksByLanguage(baseline, requiredBlockIds),
                missingPlaceholdersByKey = missingPlaceholdersByKey,
            )

        return Result(
            complete = complete,
            missingPlaceholderKeys = missingPlaceholderKeys,
            unresolvedRequiredBlockIdsByLanguage = unresolvedRequiredBlockIdsByLanguage,
            skeletonYaml = skeletonYaml,
        )
    }

    private fun applyProfileChain(
        baseline: ClearTermsBaseline,
        profileChain: List<ClearTermsProfile>,
    ): ChainState {
        var effectiveTexts = baseline.textsByLanguage
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()

        profileChain.forEach { profile ->
            mergedValues.putAll(profile.overrides.values ?: emptyMap())
            effectiveTexts = profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
        }

        if (effectiveTexts.isEmpty()) {
            throw ClearTermsValidationException("No languages available after applying profile chain")
        }

        return ChainState(
            effectiveTexts = effectiveTexts,
            mergedValues = mergedValues,
        )
    }

    private fun requiredBlockIds(baseline: ClearTermsBaseline): Set<String> =
        baseline.textsByLanguage.values
            .first()
            .filter { block -> block.text == null }
            .map { block -> block.id }
            .toSet()

    private fun unresolvedRequiredBlocks(
        effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        requiredBlockIds: Set<String>,
    ): Map<String, List<String>> =
        effectiveTexts.mapValues { (_, blocks) ->
            blocks
                .filter { block -> block.id in requiredBlockIds && block.text == null }
                .map { block -> block.id }
        }

    private fun requiredBlocksByLanguage(
        baseline: ClearTermsBaseline,
        requiredBlockIds: Set<String>,
    ): Map<String, List<RequiredTextBlockTemplate>> =
        baseline.textsByLanguage.mapValues { (_, blocks) ->
            blocks
                .filter { block -> block.id in requiredBlockIds }
                .map { block ->
                    RequiredTextBlockTemplate(
                        id = block.id,
                        todoHint = buildTodoHint(block),
                    )
                }
        }

    private fun missingPlaceholdersByKey(
        textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
        mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
    ): Map<String, ClearTermsPlaceholderType> =
        placeholderRequirements(textsByLanguage)
            .filter { (key, requirement) -> mergedValues.isMissingRequiredValue(key, requirement) }
            .mapValues { (_, requirement) -> requirement.type }
            .toList()
            .sortedBy { (key, _) -> key }
            .toMap()

    private fun Map<String, ClearTermsJsonNode.ScalarNode>.isMissingRequiredValue(
        key: String,
        requirement: PlaceholderRequirement,
    ): Boolean {
        val rawValue = this[key]
        return rawValue == null || (rawValue is ClearTermsJsonNode.ScalarNode.NullNode && !requirement.nullAllowed)
    }

    private fun placeholderRequirements(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, PlaceholderRequirement> {
        val requirements = linkedMapOf<String, PlaceholderRequirement>()
        textsByLanguage.values.flatten().forEach { block ->
            val text = block.text ?: return@forEach
            placeholderParser
                .parse(text)
                .filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>()
                .map { segment -> segment.placeholder }
                .forEach { placeholder ->
                    requirements[placeholder.key] = mergeRequirement(requirements[placeholder.key], placeholder)
                }
        }
        return requirements
    }

    private fun mergeRequirement(
        existing: PlaceholderRequirement?,
        placeholder: ClearTermsPlaceholder,
    ): PlaceholderRequirement {
        val nullAllowed = placeholder.fallback != null
        if (existing == null) {
            return PlaceholderRequirement(placeholder.type, nullAllowed)
        }
        if (existing.type != placeholder.type) {
            throw ClearTermsValidationException("Placeholder type mismatch for '${placeholder.key}'")
        }
        return PlaceholderRequirement(
            type = existing.type,
            nullAllowed = existing.nullAllowed && nullAllowed,
        )
    }

    private fun isComplete(
        unresolvedRequiredBlockIdsByLanguage: Map<String, List<String>>,
        missingPlaceholderKeys: List<String>,
    ): Boolean =
        unresolvedRequiredBlockIdsByLanguage.values.all { ids -> ids.isEmpty() } &&
            missingPlaceholderKeys.isEmpty()

    private fun parentReference(
        baseline: ClearTermsBaseline,
        profileChain: List<ClearTermsProfile>,
    ): ParentReference {
        val parentProfile = profileChain.lastOrNull()
        return if (parentProfile == null) {
            ParentReference(
                id = baseline.id,
                version = baseline.version,
            )
        } else {
            ParentReference(
                id = parentProfile.id,
                version = parentProfile.version,
            )
        }
    }

    private fun buildTodoHint(block: ClearTermsTextBlock): String {
        val expectedContent = block.hints?.expectedContent
        if (expectedContent != null) {
            return "TODO: ${expectedContent.singleLine()}"
        }
        return "TODO: Provide required service-specific text."
    }

    private fun skeletonYaml(
        parentId: String,
        parentVersion: String,
        requiredBlocksByLanguage: Map<String, List<RequiredTextBlockTemplate>>,
        missingPlaceholdersByKey: Map<String, ClearTermsPlaceholderType>,
    ): String {
        val yaml = StringBuilder()
        yaml.appendLine("kind: profile")
        yaml.appendLine("""id: "$SKELETON_PROFILE_ID_PLACEHOLDER"""")
        yaml.appendLine("""version: "$SKELETON_PROFILE_VERSION_PLACEHOLDER"""")
        yaml.appendLine("parent:")
        yaml.appendLine("""  id: "${escapeYamlDoubleQuoted(parentId)}"""")
        yaml.appendLine("""  version: "${escapeYamlDoubleQuoted(parentVersion)}"""")
        yaml.appendLine("overrides:")
        yaml.appendLine("  values:")
        if (missingPlaceholdersByKey.isEmpty()) {
            yaml.appendLine("    {}")
        } else {
            missingPlaceholdersByKey.forEach { (key, type) ->
                yaml.appendLine("""    ${yamlKey(key)}: null # type: ${type.name.lowercase()}""")
            }
        }
        yaml.appendLine("  texts:")
        requiredBlocksByLanguage.entries.sortedBy { (language, _) -> language }.forEach { (language, blocks) ->
            yaml.appendLine("    ${yamlKey(language)}:")
            yaml.appendLine("      replace:")
            if (blocks.isEmpty()) {
                yaml.appendLine("        {}")
            } else {
                blocks.forEach { block ->
                    yaml.appendLine("""        ${yamlKey(block.id)}: null # ${block.todoHint}""")
                }
            }
        }
        return yaml.toString().trimEnd()
    }

    private fun yamlKey(key: String): String = key

    private fun escapeYamlDoubleQuoted(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

    private fun String.singleLine(): String = replace("\n", " ").replace("\r", " ").trim()

    private data class ChainState(
        val effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
        val mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
    )

    private data class ParentReference(
        val id: String,
        val version: String,
    )

    private data class RequiredTextBlockTemplate(
        val id: String,
        val todoHint: String,
    )

    private data class PlaceholderRequirement(
        val type: ClearTermsPlaceholderType,
        val nullAllowed: Boolean,
    )

    private companion object {
        private const val SKELETON_PROFILE_ID_PLACEHOLDER = "TODO_PROFILE_ID"
        private const val SKELETON_PROFILE_VERSION_PLACEHOLDER = "TODO_PROFILE_VERSION"
    }
}
