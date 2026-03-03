package straightway.mosa.legal.clearterms

class ClearTermsProfileChainCompletenessChecker(
    private val parser: ClearTermsJsonParser = ClearTermsJsonParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val profileValidator: ClearTermsProfileValidator =
        ClearTermsProfileValidator(ClearTermsPlaceholderParser()),
) {
    fun analyze(
        baselineCanonicalJson: String,
        profileChainCanonicalJsons: List<String>,
    ): Result {
        val baseline = reader.readBaseline(parser.parse(baselineCanonicalJson))
        profileValidator.validateBaselineStructure(baseline)
        val requiredBlockIds = requiredBlockIds(baseline)

        var effectiveTexts = baseline.textsByLanguage
        val mergedValues = linkedMapOf<String, ClearTermsJsonNode.ScalarNode>()

        val profileChain = profileChainCanonicalJsons.map { reader.readProfile(parser.parse(it)) }
        profileChain.forEach { profile ->
            mergedValues.putAll(profile.overrides.values ?: emptyMap())
            effectiveTexts = profileValidator.applyTextOverrides(effectiveTexts, profile.overrides.texts)
        }

        val unresolvedRequiredBlockIdsByLanguage = unresolvedRequiredBlocks(effectiveTexts, requiredBlockIds)
        val placeholders = placeholderRequirements(effectiveTexts)
        val missingPlaceholdersByKey =
            placeholders
                .filter { (key, requirement) ->
                    val rawValue = mergedValues[key]
                    rawValue == null || (rawValue is ClearTermsJsonNode.ScalarNode.NullNode && !requirement.nullAllowed)
                }.mapValues { (_, requirement) -> requirement.type }
                .toList()
                .sortedBy { (key, _) -> key }
                .toMap()
        val missingPlaceholderKeys = missingPlaceholdersByKey.keys.toList()
        val complete =
            unresolvedRequiredBlockIdsByLanguage.values.all { ids -> ids.isEmpty() } &&
                missingPlaceholderKeys.isEmpty()

        val (parentId, parentVersion) =
            if (profileChain.isEmpty()) {
                baseline.id to baseline.version
            } else {
                val parentProfile = profileChain.last()
                parentProfile.id to parentProfile.version
            }
        val skeletonYaml =
            skeletonYaml(
                parentId = parentId,
                parentVersion = parentVersion,
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

    private fun placeholderRequirements(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, PlaceholderRequirement> {
        val requirements = linkedMapOf<String, PlaceholderRequirement>()
        textsByLanguage.values.flatten().forEach { block ->
            val text = block.text ?: return@forEach
            placeholderParser
                .parse(text)
                .filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>()
                .forEach { segment ->
                    val placeholder = segment.placeholder
                    val existing = requirements[placeholder.key]
                    if (existing == null) {
                        requirements[placeholder.key] = PlaceholderRequirement(placeholder.type, placeholder.fallback != null)
                    } else {
                        if (existing.type != placeholder.type) {
                            throw ClearTermsValidationException("Placeholder type mismatch for '${placeholder.key}'")
                        }
                        requirements[placeholder.key] =
                            PlaceholderRequirement(
                                type = existing.type,
                                nullAllowed = existing.nullAllowed && placeholder.fallback != null,
                            )
                    }
                }
        }
        return requirements
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

    data class Result(
        val complete: Boolean,
        val missingPlaceholderKeys: List<String>,
        val unresolvedRequiredBlockIdsByLanguage: Map<String, List<String>>,
        val skeletonYaml: String,
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
