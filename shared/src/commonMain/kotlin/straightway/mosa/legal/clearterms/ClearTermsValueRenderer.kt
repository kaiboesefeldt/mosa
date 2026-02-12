package straightway.mosa.legal.clearterms

class ClearTermsValueRenderer(
    private val placeholderParser: ClearTermsPlaceholderParser,
    private val localeProvider: ClearTermsLocaleProvider,
    private val numberRenderer: ClearTermsNumberRenderer = ClearTermsNumberRenderer(),
    private val timeTextRenderer: ClearTermsTimeTextRenderer = ClearTermsTimeTextRenderer(),
) {
    fun renderBlocks(
        blocks: List<ClearTermsTextBlock>,
        values: Map<String, ClearTermsValue>,
        languageTag: String,
    ): String {
        val rendered = blocks.joinToString("") { block -> renderText(block.text, values, languageTag) }
        if (containsUnresolvedPlaceholder(rendered)) {
            throw ClearTermsValidationException("Rendered text contains unresolved placeholders")
        }
        return rendered
    }

    private fun renderText(
        text: String,
        values: Map<String, ClearTermsValue>,
        languageTag: String,
    ): String {
        val segments = placeholderParser.parse(text)
        val result = StringBuilder()
        segments.forEach { segment ->
            when (segment) {
                is ClearTermsPlaceholderParser.Segment.Literal -> result.append(segment.text)
                is ClearTermsPlaceholderParser.Segment.Placeholder ->
                    result.append(
                        renderPlaceholder(segment.placeholder, values, languageTag),
                    )
            }
        }
        return result.toString()
    }

    private fun renderPlaceholder(
        placeholder: ClearTermsPlaceholder,
        values: Map<String, ClearTermsValue>,
        languageTag: String,
    ): String {
        val value =
            values[placeholder.key]
                ?: throw ClearTermsValidationException("Missing value for '${placeholder.key}'")
        return renderValue(value, placeholder, languageTag)
    }

    private fun renderValue(
        value: ClearTermsValue,
        placeholder: ClearTermsPlaceholder,
        languageTag: String,
    ): String {
        val raw = value.value
        if (raw == null) {
            return placeholder.fallback
                ?: throw ClearTermsValidationException("Missing fallback for '${placeholder.key}'")
        }
        val locale = localeProvider.resolve(languageTag)
        return when (value.type) {
            ClearTermsPlaceholderType.STRING -> raw as String
            ClearTermsPlaceholderType.NUMBER -> numberRenderer.format(raw as Double, placeholder.numberOptions, locale)
            ClearTermsPlaceholderType.DURATION -> raw as String
            ClearTermsPlaceholderType.TIME -> timeTextRenderer.renderTimeValue(raw as String, locale)
            ClearTermsPlaceholderType.WEEKDAY -> timeTextRenderer.renderWeekday(raw as String, locale)
        }
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
        val typeAndRest = main.split(":", limit = 2)
        if (typeAndRest.size != 2) {
            return false
        }
        if (!isSupportedPlaceholderType(typeAndRest[0])) {
            return false
        }
        val keyAndOptions = typeAndRest[1]
        val key = keyAndOptions.substringBefore("?")
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            return false
        }
        if (keyAndOptions.contains("?")) {
            val options = keyAndOptions.substringAfter("?")
            if (options.isBlank()) {
                return false
            }
        }
        return true
    }

    private fun isSupportedPlaceholderType(value: String): Boolean =
        value == "string" || value == "number" || value == "duration" || value == "time" || value == "weekday"
}
