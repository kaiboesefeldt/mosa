package straightway.mosa.legal.clearterms

class ClearTermsRenderer(
    private val parser: ClearTermsJsonParser,
    private val hashProvider: ClearTermsHashProvider,
    private val textEncoder: ClearTermsTextEncoder,
    private val canonicalizer: ClearTermsCanonicalJson = ClearTermsCanonicalJson(),
    private val placeholderParser: ClearTermsPlaceholderParser = ClearTermsPlaceholderParser(),
    private val reader: ClearTermsDocumentReader = ClearTermsDocumentReader(),
) {
    suspend fun renderAll(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
    ): ClearTermsRenderResult {
        val baselineNode = parser.parse(baselineJson)
        val defaultNode = parser.parse(defaultJson)
        val deviationNode = parser.parse(deviationJson)

        val baselineHash = hashFor(baselineNode)
        val defaultHash = hashFor(defaultNode)

        val baseline = reader.readBaseline(baselineNode)
        val defaultProfile = reader.readDefault(defaultNode)
        val deviationProfile = reader.readDeviation(deviationNode)

        validateReference(defaultProfile.baseline, baseline.id, baseline.version, baselineHash)
        validateReference(deviationProfile.default, defaultProfile.id, defaultProfile.version, defaultHash)

        validateBaselineStructure(baseline)

        val baselinePlaceholders = placeholdersByKey(baseline.textsByLanguage)

        val defaultValues = validateAndCoerceValues(defaultProfile.values, baselinePlaceholders, requireAll = true)

        val effectiveTexts = applyTextOverrides(baseline, deviationProfile.overrides.texts)
        val effectivePlaceholders = placeholdersByKey(effectiveTexts)

        val deviationValues = validateAndCoerceValues(deviationProfile.overrides.values, effectivePlaceholders, requireAll = false)

        val effectiveValues = defaultValues + deviationValues

        val renderedByLanguage =
            effectiveTexts.mapValues { (_, blocks) ->
                renderBlocks(blocks, effectiveValues)
            }

        return ClearTermsRenderResult(renderedByLanguage)
    }

    suspend fun renderLanguage(
        baselineJson: String,
        defaultJson: String,
        deviationJson: String,
        languageTag: String,
    ): String {
        val result = renderAll(baselineJson, defaultJson, deviationJson)
        return result.textsByLanguage[languageTag]
            ?: throw ClearTermsValidationException("Language '$languageTag' not available")
    }

    private suspend fun hashFor(node: ClearTermsJsonNode.ObjectNode): String {
        val canonical = canonicalizer.canonicalize(node)
        val digest = hashProvider.sha256(textEncoder.encode(canonical))
        return "sha256:${digest.toHexLower()}"
    }

    private fun validateReference(
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

    private fun validateBaselineStructure(baseline: ClearTermsBaseline) {
        val languages = baseline.textsByLanguage
        val referenceBlocks = languages.values.first()
        val referenceIds = referenceBlocks.map { it.id }
        referenceIds.requireUnique("Baseline block ids must be unique")
        languages.forEach { (language, blocks) ->
            val ids = blocks.map { it.id }
            ids.requireUnique("Baseline block ids must be unique for '$language'")
            if (ids != referenceIds) {
                throw ClearTermsValidationException("Baseline block alignment mismatch")
            }
        }
    }

    private fun applyTextOverrides(
        baseline: ClearTermsBaseline,
        overrides: Map<String, ClearTermsDeviationProfile.TextOverrides>?,
    ): Map<String, List<ClearTermsTextBlock>> {
        if (overrides == null) {
            return baseline.textsByLanguage
        }
        if (overrides.keys != baseline.textsByLanguage.keys) {
            throw ClearTermsValidationException("Deviation texts must cover all baseline languages")
        }
        val baselineIds =
            baseline.textsByLanguage.values
                .first()
                .map { it.id }
                .toSet()
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
        val appendReference = overrides.values.first().append
        val appendIds = appendReference.map { it.id }
        appendIds.requireUnique("Append block ids must be unique")
        if (appendIds.any { it in baselineIds }) {
            throw ClearTermsValidationException("Append block ids must not collide with baseline")
        }
        overrides.values.forEach { languageOverrides ->
            if (languageOverrides.append.map { it.id } != appendIds) {
                throw ClearTermsValidationException("Append block ids must align across languages")
            }
        }
        overrides.values.forEach { languageOverrides ->
            languageOverrides.replace.values.forEach { text ->
                ensureNoFallbacks(text)
            }
            languageOverrides.append.forEach { block ->
                ensureNoFallbacks(block.text)
            }
        }
        return baseline.textsByLanguage.mapValues { (language, blocks) ->
            val languageOverrides = overrides.getValue(language)
            val replaced =
                blocks.map { block ->
                    val replacement = languageOverrides.replace[block.id]
                    if (replacement == null) {
                        block
                    } else {
                        ClearTermsTextBlock(block.id, replacement)
                    }
                }
            replaced + languageOverrides.append
        }
    }

    private fun placeholdersByKey(textsByLanguage: Map<String, List<ClearTermsTextBlock>>): Map<String, ClearTermsPlaceholderType> {
        val placeholders = linkedMapOf<String, ClearTermsPlaceholderType>()
        textsByLanguage.values.flatten().forEach { block ->
            val segments = placeholderParser.parse(block.text)
            segments.filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>().forEach { segment ->
                val key = segment.placeholder.key
                val type = segment.placeholder.type
                val existing = placeholders[key]
                if (existing != null && existing != type) {
                    throw ClearTermsValidationException("Placeholder type mismatch for '$key'")
                }
                placeholders[key] = type
            }
        }
        return placeholders
    }

    private fun ensureNoFallbacks(text: String) {
        val segments = placeholderParser.parse(text)
        segments.filterIsInstance<ClearTermsPlaceholderParser.Segment.Placeholder>().forEach { segment ->
            if (segment.placeholder.fallback != null) {
                throw ClearTermsValidationException("Fallbacks are not allowed in deviation text")
            }
        }
    }

    private fun validateAndCoerceValues(
        rawValues: Map<String, ClearTermsJsonNode.ScalarNode>,
        types: Map<String, ClearTermsPlaceholderType>,
        requireAll: Boolean,
    ): Map<String, ClearTermsValue> {
        val missing = types.keys.firstOrNull { requireAll && it !in rawValues.keys }
        if (missing != null) {
            throw ClearTermsValidationException("Missing value for '$missing'")
        }
        return rawValues.mapValues { (key, raw) ->
            val type =
                types[key]
                    ?: throw ClearTermsValidationException("Unknown placeholder '$key'")
            val value =
                when (raw) {
                    is ClearTermsJsonNode.ScalarNode.StringNode -> raw.value
                    is ClearTermsJsonNode.ScalarNode.NumberNode -> raw.value
                    ClearTermsJsonNode.ScalarNode.NullNode -> null
                }
            validateValue(type, value)
            ClearTermsValue(type, value)
        }
    }

    private fun validateValue(
        type: ClearTermsPlaceholderType,
        value: Any?,
    ) {
        when (type) {
            ClearTermsPlaceholderType.STRING -> {
                if (value !is String) {
                    throw ClearTermsValidationException("String value must be provided")
                }
            }
            ClearTermsPlaceholderType.NUMBER -> {
                if (value != null && value !is Double) {
                    throw ClearTermsValidationException("Number value must be numeric")
                }
            }
            ClearTermsPlaceholderType.DURATION -> {
                if (value != null) {
                    val text =
                        value as? String
                            ?: throw ClearTermsValidationException("Duration value must be string")
                    val normalized = normalizeScalarString(text)
                    if (!isValidDuration(normalized)) {
                        throw ClearTermsValidationException("Invalid duration value '$normalized'")
                    }
                }
            }
            ClearTermsPlaceholderType.TIME -> {
                if (value != null) {
                    val text =
                        value as? String
                            ?: throw ClearTermsValidationException("Time value must be string")
                    val normalized = normalizeScalarString(text)
                    if (!timeRegex.matches(normalized)) {
                        throw ClearTermsValidationException("Invalid time value '$normalized'")
                    }
                }
            }
            ClearTermsPlaceholderType.WEEKDAY -> {
                if (value != null) {
                    val text =
                        value as? String
                            ?: throw ClearTermsValidationException("Weekday value must be string")
                    val normalized = normalizeScalarString(text)
                    if (!weekdayRegex.matches(normalized)) {
                        throw ClearTermsValidationException("Invalid weekday value '$normalized'")
                    }
                }
            }
        }
    }

    private fun renderBlocks(
        blocks: List<ClearTermsTextBlock>,
        values: Map<String, ClearTermsValue>,
    ): String {
        val rendered = blocks.joinToString("") { block -> renderText(block.text, values) }
        if (containsUnresolvedPlaceholder(rendered)) {
            throw ClearTermsValidationException("Rendered text contains unresolved placeholders")
        }
        return rendered
    }

    private fun renderText(
        text: String,
        values: Map<String, ClearTermsValue>,
    ): String {
        val segments = placeholderParser.parse(text)
        val result = StringBuilder()
        segments.forEach { segment ->
            when (segment) {
                is ClearTermsPlaceholderParser.Segment.Literal -> result.append(segment.text)
                is ClearTermsPlaceholderParser.Segment.Placeholder -> {
                    val placeholder = segment.placeholder
                    val value =
                        values[placeholder.key]
                            ?: throw ClearTermsValidationException("Missing value for '${placeholder.key}'")
                    val rendered = renderValue(value, placeholder)
                    result.append(rendered)
                }
            }
        }
        return result.toString()
    }

    private fun containsUnresolvedPlaceholder(text: String): Boolean {
        var index = 0
        while (index < text.length - 1) {
            if (text.startsWith("{{", index)) {
                val endIndex = text.indexOf("}}", index + 2)
                if (endIndex < 0) {
                    throw ClearTermsValidationException("Rendered text contains unterminated placeholder")
                }
                val content = text.substring(index + 2, endIndex)
                if (matchesPlaceholderSyntax(content)) {
                    return true
                }
                index = endIndex + 2
            } else {
                index += 1
            }
        }
        return false
    }

    private fun matchesPlaceholderSyntax(content: String): Boolean {
        val parts = content.split("??", limit = 2)
        val main = parts[0]
        val typeAndKey = main.split(":", limit = 2)
        if (typeAndKey.size != 2) {
            return false
        }
        val type =
            when (typeAndKey[0]) {
                "string", "number", "duration", "time", "weekday" -> true
                else -> false
            }
        if (!type) {
            return false
        }
        val key = typeAndKey[1]
        return key.isNotBlank() && key.none { it.isWhitespace() }
    }

    private fun renderValue(
        value: ClearTermsValue,
        placeholder: ClearTermsPlaceholder,
    ): String {
        val raw = value.value
        if (raw == null) {
            return placeholder.fallback
                ?: throw ClearTermsValidationException("Missing fallback for '${placeholder.key}'")
        }
        return when (value.type) {
            ClearTermsPlaceholderType.STRING -> raw as String
            ClearTermsPlaceholderType.NUMBER -> formatNumber(raw as Double)
            ClearTermsPlaceholderType.DURATION -> raw as String
            ClearTermsPlaceholderType.TIME -> raw as String
            ClearTermsPlaceholderType.WEEKDAY -> raw as String
        }
    }

    private fun formatNumber(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }

    private fun normalizeScalarString(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('\"') && trimmed.endsWith('\"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun isValidDuration(value: String): Boolean {
        if (value == "infinite") {
            return true
        }
        if (value.length < 2) {
            return false
        }
        val unit = value.last()
        if (unit !in setOf('s', 'm', 'h', 'd', 'w', 'y')) {
            return false
        }
        val number = value.dropLast(1)
        return number.toDoubleOrNull() != null
    }

    private fun ByteArray.toHexLower(): String =
        joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            value.toString(16).padStart(2, '0')
        }

    private fun List<String>.requireUnique(message: String) {
        val duplicates = groupBy { it }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw ClearTermsValidationException(message)
        }
    }

    private companion object {
        val timeRegex = Regex("""^(\*|\d{4})-(\*|\d{2})-(\*|\d{2})(T(\*|\d{2}):(\*|\d{2})(:(\*|\d{2}))?(Z|[+-]\d{2}:\d{2}))?$""")
        val weekdayRegex = Regex("""^(mon|tue|wed|thu|fri|sat|sun)$""")
    }
}
