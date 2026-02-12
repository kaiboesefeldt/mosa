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
        languages.forEach { (language, blocks) ->
            val languageIds = blocks.map { it.id }
            requireUnique(languageIds, "Baseline block ids must be unique for '$language'")
            if (languageIds != referenceIds) {
                throw ClearTermsValidationException("Baseline block alignment mismatch")
            }
        }
    }

    fun applyTextOverrides(
        baseline: ClearTermsBaseline,
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>?,
    ): Map<String, List<ClearTermsTextBlock>> {
        if (overrides == null) {
            return baseline.textsByLanguage
        }
        validateOverrideLanguages(overrides, baseline)
        validateReplaceDefinitions(overrides, baseline)
        validateAppendDefinitions(overrides, baseline)
        validateOverrideTextsDoNotUseFallbacks(overrides)
        return baseline.textsByLanguage.mapValues { (language, blocks) ->
            mergeLanguageBlocks(blocks, overrides.getValue(language))
        }
    }

    fun placeholdersByKey(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, ClearTermsPlaceholderType> {
        val placeholders = linkedMapOf<String, ClearTermsPlaceholderType>()
        textsByLanguage.values.flatten().forEach { block ->
            placeholderSegments(block.text).forEach { placeholder ->
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

    private fun validateOverrideLanguages(
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>,
        baseline: ClearTermsBaseline,
    ) {
        if (overrides.keys != baseline.textsByLanguage.keys) {
            throw ClearTermsValidationException("Deviation texts must cover all baseline languages")
        }
    }

    private fun validateReplaceDefinitions(
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>,
        baseline: ClearTermsBaseline,
    ) {
        val baselineIds = baselineBlockIdSet(baseline)
        val replaceKeys =
            overrides.values
                .first()
                .replace.keys
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.replace.keys != replaceKeys) {
                throw ClearTermsValidationException("Replace block ids must match across languages")
            }
            val missing = languageOverrides.replace.keys.firstOrNull { it !in baselineIds }
            if (missing != null) {
                throw ClearTermsValidationException("Replace block id '$missing' does not exist")
            }
        }
    }

    private fun validateAppendDefinitions(
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>,
        baseline: ClearTermsBaseline,
    ) {
        val baselineIds = baselineBlockIdSet(baseline)
        val appendReference = overrides.values.first().append
        val appendIds = appendReference.map { it.id }
        requireUnique(appendIds, "Append block ids must be unique")
        if (appendIds.any { it in baselineIds }) {
            throw ClearTermsValidationException("Append block ids must not collide with baseline")
        }
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.append.map { it.id } != appendIds) {
                throw ClearTermsValidationException("Append block ids must align across languages")
            }
        }
    }

    private fun validateOverrideTextsDoNotUseFallbacks(overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>) {
        overrides.values.forEach { languageOverrides ->
            languageOverrides.replace.values.forEach { text ->
                ensureNoFallbacks(text)
            }
            languageOverrides.append.forEach { block ->
                ensureNoFallbacks(block.text)
            }
        }
    }

    private fun ensureNoFallbacks(text: String) {
        placeholderSegments(text).forEach { placeholder ->
            if (placeholder.fallback != null) {
                throw ClearTermsValidationException("Fallbacks are not allowed in deviation text")
            }
        }
    }

    private fun referenceBlockIds(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): List<String> =
        textsByLanguage.values.first().map { it.id }

    private fun baselineBlockIdSet(baseline: ClearTermsBaseline): Set<String> = referenceBlockIds(baseline.textsByLanguage).toSet()

    private fun mergeLanguageBlocks(
        baselineBlocks: List<ClearTermsTextBlock>,
        overrides: ClearTermsDeviationProfile.TextOverrides,
    ): List<ClearTermsTextBlock> {
        val replaced =
            baselineBlocks.map { block ->
                val replacement = overrides.replace[block.id]
                if (replacement == null) {
                    block
                } else {
                    ClearTermsTextBlock(block.id, replacement)
                }
            }
        return replaced + overrides.append
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
