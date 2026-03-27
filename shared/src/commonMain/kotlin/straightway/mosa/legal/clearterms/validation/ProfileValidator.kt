package straightway.mosa.legal.clearterms.validation

import straightway.mosa.legal.clearterms.document.Baseline
import straightway.mosa.legal.clearterms.document.Profile
import straightway.mosa.legal.clearterms.document.Reference
import straightway.mosa.legal.clearterms.document.TextBlock
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.value.Value
import straightway.mosa.legal.clearterms.placeholder.Placeholder as ParsedPlaceholder

class ProfileValidator(
    private val placeholderParser: PlaceholderParser,
    private val valueTypeValidator: ValueTypeValidator = ValueTypeValidator(),
) {
    fun validateReference(
        reference: Reference,
        id: String,
        version: String,
        hash: String,
    ) {
        if (reference.id != id) {
            throw ValidationException("Reference id mismatch")
        }
        if (reference.version != version) {
            throw ValidationException("Reference version mismatch")
        }
        if (reference.hash.lowercase() != hash.lowercase()) {
            throw ValidationException("Reference hash mismatch")
        }
    }

    fun validateBaselineStructure(baseline: Baseline) {
        val languages = baseline.textsByLanguage
        val referenceIds = referenceBlockIds(languages)
        requireUnique(referenceIds, "Baseline block ids must be unique")
        val referenceNullFlags = languages.values.first().map { it.text == null }
        languages.forEach { (language, blocks) ->
            val languageIds = blocks.map { it.id }
            requireUnique(languageIds, "Baseline block ids must be unique for '$language'")
            if (languageIds != referenceIds) {
                throw ValidationException("Baseline block alignment mismatch")
            }
            val languageNullFlags = blocks.map { it.text == null }
            if (languageNullFlags != referenceNullFlags) {
                throw ValidationException("Baseline null block alignment mismatch")
            }
        }
    }

    fun applyTextOverrides(
        textsByLanguage: Map<String, List<TextBlock>>,
        overrides: Map<String, Profile.TextOverrides>?,
    ): Map<String, List<TextBlock>> {
        if (overrides == null) {
            return textsByLanguage
        }
        validateOverrideLanguages(overrides, textsByLanguage)
        validateReplaceDefinitions(overrides, textsByLanguage)
        validateAppendDefinitions(overrides, textsByLanguage)
        return overrides.mapValues { (language, languageOverrides) ->
            mergeLanguageBlocks(textsByLanguage.getValue(language), languageOverrides)
        }
    }

    fun placeholdersByKey(textsByLanguage: Map<String, List<TextBlock>>): Map<String, PlaceholderType> {
        val placeholders = linkedMapOf<String, PlaceholderType>()
        textsByLanguage.values.flatten().forEach { block ->
            val text = block.text ?: return@forEach
            placeholderSegments(text).forEach { placeholder ->
                val existing = placeholders[placeholder.key]
                if (existing != null && existing != placeholder.type) {
                    throw ValidationException("Placeholder type mismatch for '${placeholder.key}'")
                }
                placeholders[placeholder.key] = placeholder.type
            }
        }
        return placeholders
    }

    fun validateAndCoerceValues(
        rawValues: Map<String, JsonNode.ScalarNode>,
        types: Map<String, PlaceholderType>,
        requireAll: Boolean,
    ): Map<String, Value> {
        requireAllValuesPresent(rawValues, types, requireAll)
        return types.mapValues { (key, type) ->
            val raw = rawValues[key] ?: JsonNode.ScalarNode.NullNode
            val value = scalarNodeValue(raw)
            valueTypeValidator.validate(type, value)
            Value(type, value)
        }
    }

    fun validateFinalTextsResolved(textsByLanguage: Map<String, List<TextBlock>>) {
        if (textsByLanguage.isEmpty()) {
            throw ValidationException("No languages available after applying profile chain")
        }
        textsByLanguage.forEach { (language, blocks) ->
            val unresolved = blocks.firstOrNull { it.text == null }
            if (unresolved != null) {
                throw ValidationException("Text block '${unresolved.id}' is unresolved for '$language'")
            }
        }
    }

    private fun validateOverrideLanguages(
        overrides: Map<String, Profile.TextOverrides>,
        textsByLanguage: Map<String, List<TextBlock>>,
    ) {
        val unsupportedLanguage = overrides.keys.firstOrNull { it !in textsByLanguage.keys }
        if (unsupportedLanguage != null) {
            throw ValidationException("Override language '$unsupportedLanguage' is not supported by parent profile")
        }
    }

    private fun validateReplaceDefinitions(
        overrides: Map<String, Profile.TextOverrides>,
        textsByLanguage: Map<String, List<TextBlock>>,
    ) {
        if (overrides.isEmpty()) {
            return
        }
        val existingIds = blockIdSet(textsByLanguage)
        val replaceKeys =
            overrides.values
                .first()
                .replace.keys
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.replace.keys != replaceKeys) {
                throw ValidationException("Replace block ids must match across languages")
            }
            val missing = languageOverrides.replace.keys.firstOrNull { it !in existingIds }
            if (missing != null) {
                throw ValidationException("Replace block id '$missing' does not exist")
            }
        }
    }

    private fun validateAppendDefinitions(
        overrides: Map<String, Profile.TextOverrides>,
        textsByLanguage: Map<String, List<TextBlock>>,
    ) {
        if (overrides.isEmpty()) {
            return
        }
        val existingIds = blockIdSet(textsByLanguage)
        val appendReference =
            overrides.values
                .first()
                .append
                .orEmpty()
        val appendIds = appendReference.map { it.id }
        requireUnique(appendIds, "Append block ids must be unique")
        if (appendIds.any { it in existingIds }) {
            throw ValidationException("Append block ids must not collide with baseline")
        }
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.append.orEmpty().map { it.id } != appendIds) {
                throw ValidationException("Append block ids must align across languages")
            }
        }
    }

    private fun referenceBlockIds(textsByLanguage: Map<String, List<TextBlock>>): List<String> =
        textsByLanguage.values.first().map { it.id }

    private fun blockIdSet(textsByLanguage: Map<String, List<TextBlock>>): Set<String> = referenceBlockIds(textsByLanguage).toSet()

    private fun mergeLanguageBlocks(
        baselineBlocks: List<TextBlock>,
        overrides: Profile.TextOverrides,
    ): List<TextBlock> {
        val replaced =
            baselineBlocks.map { block ->
                val replacement = overrides.replace[block.id]
                if (replacement == null) {
                    block
                } else {
                    TextBlock(block.id, replacement, block.hints)
                }
            }
        return replaced + overrides.append.orEmpty()
    }

    private fun requireAllValuesPresent(
        rawValues: Map<String, JsonNode.ScalarNode>,
        types: Map<String, PlaceholderType>,
        requireAll: Boolean,
    ) {
        val missing = types.keys.firstOrNull { requireAll && it !in rawValues.keys }
        if (missing != null) {
            throw ValidationException("Missing value for '$missing'")
        }
    }

    private fun scalarNodeValue(node: JsonNode.ScalarNode): Any? =
        when (node) {
            is JsonNode.ScalarNode.StringNode -> node.value
            is JsonNode.ScalarNode.NumberNode -> node.value
            is JsonNode.ScalarNode.BooleanNode -> node.value
            JsonNode.ScalarNode.NullNode -> null
        }

    private fun placeholderSegments(text: String): List<ParsedPlaceholder> =
        placeholderParser
            .parse(text)
            .filterIsInstance<PlaceholderParser.Segment.Placeholder>()
            .map { it.placeholder }

    private fun requireUnique(
        values: List<String>,
        message: String,
    ) {
        val duplicates = values.groupBy { it }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw ValidationException(message)
        }
    }
}
