package straightway.mosa.legal.clearterms

class ClearTermsProfileValidator(
    private val placeholderParser: ClearTermsPlaceholderParser,
    private val valueTypeValidator: ClearTermsValueTypeValidator = ClearTermsValueTypeValidator(),
) {
    fun validateReference(
        reference: ClearTermsReference,
        id: String,
        version: String,
        hash: String,
    ) {
        if (reference.id != id) {
            throw ClearTermsValidationException("Reference id mismatch")
        }
        if (reference.version != version) {
            throw ClearTermsValidationException("Reference version mismatch")
        }
        if (reference.hash.lowercase() != hash.lowercase()) {
            throw ClearTermsValidationException("Reference hash mismatch")
        }
    }

    fun validateBaselineStructure(baseline: ClearTermsBaseline) {
        val languages = baseline.textsByLanguage
        val referenceIds = referenceBlockIds(languages)
        requireUnique(referenceIds, "Baseline block ids must be unique")
        val referenceNullFlags = languages.values.first().map { it.text == null }
        languages.forEach { (language, blocks) ->
            val languageIds = blocks.map { it.id }
            requireUnique(languageIds, "Baseline block ids must be unique for '$language'")
            if (languageIds != referenceIds) {
                throw ClearTermsValidationException("Baseline block alignment mismatch")
            }
            val languageNullFlags = blocks.map { it.text == null }
            if (languageNullFlags != referenceNullFlags) {
                throw ClearTermsValidationException("Baseline null block alignment mismatch")
            }
        }
    }

    fun applyTextOverrides(
        textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
        overrides: Map<String, ClearTermsProfile.TextOverrides>?,
    ): Map<String, List<ClearTermsTextBlock>> {
        if (overrides == null) {
            return textsByLanguage
        }
        validateOverrideLanguages(overrides, textsByLanguage)
        validateReplaceDefinitions(overrides, textsByLanguage)
        validateAppendDefinitions(overrides, textsByLanguage)
        return textsByLanguage.mapValues { (language, blocks) ->
            mergeLanguageBlocks(blocks, overrides.getValue(language))
        }
    }

    fun placeholdersByKey(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, ClearTermsPlaceholderType> {
        val placeholders = linkedMapOf<String, ClearTermsPlaceholderType>()
        textsByLanguage.values.flatten().forEach { block ->
            val text = block.text ?: return@forEach
            placeholderSegments(text).forEach { placeholder ->
                val existing = placeholders[placeholder.key]
                if (existing != null && existing != placeholder.type) {
                    throw ClearTermsValidationException("Placeholder type mismatch for '${placeholder.key}'")
                }
                placeholders[placeholder.key] = placeholder.type
            }
        }
        return placeholders
    }

    fun validateAndCoerceValues(
        rawValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        types: Map<String, ClearTermsPlaceholderType>,
        requireAll: Boolean,
    ): Map<String, ClearTermsValue> {
        requireAllValuesPresent(rawValues, types, requireAll)
        return rawValues.mapValues { (key, raw) ->
            val type = types[key] ?: throw ClearTermsValidationException("Unknown placeholder '$key'")
            val value = scalarNodeValue(raw)
            valueTypeValidator.validate(type, value)
            ClearTermsValue(type, value)
        }
    }

    fun validateFinalTextsResolved(textsByLanguage: Map<String, List<ClearTermsTextBlock>>) {
        textsByLanguage.forEach { (language, blocks) ->
            val unresolved = blocks.firstOrNull { it.text == null }
            if (unresolved != null) {
                throw ClearTermsValidationException("Text block '${unresolved.id}' is unresolved for '$language'")
            }
        }
    }

    private fun validateOverrideLanguages(
        overrides: Map<String, ClearTermsProfile.TextOverrides>,
        textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
    ) {
        if (overrides.keys != textsByLanguage.keys) {
            throw ClearTermsValidationException("Deviation texts must cover all baseline languages")
        }
    }

    private fun validateReplaceDefinitions(
        overrides: Map<String, ClearTermsProfile.TextOverrides>,
        textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
    ) {
        val existingIds = blockIdSet(textsByLanguage)
        val replaceKeys =
            overrides.values
                .first()
                .replace.keys
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.replace.keys != replaceKeys) {
                throw ClearTermsValidationException("Replace block ids must match across languages")
            }
            val missing = languageOverrides.replace.keys.firstOrNull { it !in existingIds }
            if (missing != null) {
                throw ClearTermsValidationException("Replace block id '$missing' does not exist")
            }
        }
    }

    private fun validateAppendDefinitions(
        overrides: Map<String, ClearTermsProfile.TextOverrides>,
        textsByLanguage: Map<String, List<ClearTermsTextBlock>>,
    ) {
        val existingIds = blockIdSet(textsByLanguage)
        val appendReference =
            overrides.values
                .first()
                .append
                .orEmpty()
        val appendIds = appendReference.map { it.id }
        requireUnique(appendIds, "Append block ids must be unique")
        if (appendIds.any { it in existingIds }) {
            throw ClearTermsValidationException("Append block ids must not collide with baseline")
        }
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.append.orEmpty().map { it.id } != appendIds) {
                throw ClearTermsValidationException("Append block ids must align across languages")
            }
        }
    }

    private fun referenceBlockIds(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): List<String> =
        textsByLanguage.values.first().map { it.id }

    private fun blockIdSet(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Set<String> =
        referenceBlockIds(textsByLanguage).toSet()

    private fun mergeLanguageBlocks(
        baselineBlocks: List<ClearTermsTextBlock>,
        overrides: ClearTermsProfile.TextOverrides,
    ): List<ClearTermsTextBlock> {
        val replaced =
            baselineBlocks.map { block ->
                val replacement = overrides.replace[block.id]
                if (replacement == null) {
                    block
                } else {
                    ClearTermsTextBlock(block.id, replacement, block.hints)
                }
            }
        return replaced + overrides.append.orEmpty()
    }

    private fun requireAllValuesPresent(
        rawValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        types: Map<String, ClearTermsPlaceholderType>,
        requireAll: Boolean,
    ) {
        val missing = types.keys.firstOrNull { requireAll && it !in rawValues.keys }
        if (missing != null) {
            throw ClearTermsValidationException("Missing value for '$missing'")
        }
    }

    private fun scalarNodeValue(node: ClearTermsJsonNode.ScalarNode): Any? =
        when (node) {
            is ClearTermsJsonNode.ScalarNode.StringNode -> node.value
            is ClearTermsJsonNode.ScalarNode.NumberNode -> node.value
            ClearTermsJsonNode.ScalarNode.NullNode -> null
        }

    private fun placeholderSegments(text: String): List<ClearTermsPlaceholder> =
        placeholderParser
            .parse(text)
            .filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>()
            .map { it.placeholder }

    private fun requireUnique(
        values: List<String>,
        message: String,
    ) {
        val duplicates = values.groupBy { it }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw ClearTermsValidationException(message)
        }
    }
}
