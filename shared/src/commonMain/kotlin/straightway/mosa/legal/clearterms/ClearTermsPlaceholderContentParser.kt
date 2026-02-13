package straightway.mosa.legal.clearterms

class ClearTermsPlaceholderContentParser {
    fun parse(content: String): ClearTermsPlaceholder {
        val (main, fallback) = splitFallback(content)
        val (typeToken, keyAndOptions) = splitTypeAndContent(main)
        val type = parseType(typeToken)
        val (key, options) = splitOptions(keyAndOptions)
        validatePlaceholderKey(key)
        val numberOptions = parseNumberOptions(type, options)
        return ClearTermsPlaceholder(type, key, fallback, numberOptions)
    }

    private fun splitTypeAndContent(main: String): Pair<String, String> {
        val parts = main.split(":", limit = 2)
        if (parts.size != 2) {
            throw ClearTermsValidationException("Invalid placeholder syntax")
        }
        return parts[0] to parts[1]
    }

    private fun validatePlaceholderKey(key: String) {
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            throw ClearTermsValidationException("Invalid placeholder key")
        }
    }

    private fun splitFallback(content: String): Pair<String, String?> {
        val markerIndex = content.indexOf("??")
        if (markerIndex < 0) {
            return content to null
        }
        val main = content.substring(0, markerIndex)
        val fallback = content.substring(markerIndex + 2)
        if (fallback.contains("??")) {
            throw ClearTermsValidationException("Fallback must not contain '??'")
        }
        return main to fallback
    }

    private fun splitOptions(content: String): Pair<String, String?> {
        val optionsIndex = content.indexOf("?")
        if (optionsIndex < 0) {
            return content to null
        }
        val key = content.substring(0, optionsIndex)
        val options = content.substring(optionsIndex + 1)
        if (options.isBlank()) {
            throw ClearTermsValidationException("Invalid placeholder options")
        }
        return key to options
    }

    private fun parseNumberOptions(
        type: ClearTermsPlaceholderType,
        options: String?,
    ): ClearTermsNumberOptions? {
        if (options == null) {
            return null
        }
        if (type != ClearTermsPlaceholderType.NUMBER) {
            throw ClearTermsValidationException("Options are only supported for number placeholders")
        }
        val values = parseOptionEntries(options)
        val unknown = values.keys.firstOrNull { it !in supportedNumberOptionKeys }
        if (unknown != null) {
            throw ClearTermsValidationException("Unknown number placeholder option '$unknown'")
        }
        val format = values["format"]?.let { parseNumberFormat(it) } ?: ClearTermsNumberFormat.Auto
        val grouping = values["grouping"]?.let { parseNumberGrouping(it) } ?: ClearTermsNumberGrouping.ON
        return ClearTermsNumberOptions(format, grouping)
    }

    private fun parseOptionEntries(options: String): Map<String, String> {
        val entries = options.split("&")
        val values = linkedMapOf<String, String>()
        entries.forEach { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw ClearTermsValidationException("Invalid number placeholder options")
            }
            if (values.containsKey(parts[0])) {
                throw ClearTermsValidationException("Duplicate number placeholder option '${parts[0]}'")
            }
            values[parts[0]] = parts[1]
        }
        return values
    }

    private fun parseNumberFormat(value: String): ClearTermsNumberFormat =
        when {
            value == "integer" -> ClearTermsNumberFormat.Integer
            value == "auto" -> ClearTermsNumberFormat.Auto
            value.startsWith("decimal(") && value.endsWith(")") -> {
                val content = value.substringAfter("decimal(").dropLast(1)
                val scale =
                    content.toIntOrNull()
                        ?: throw ClearTermsValidationException("Invalid number format '$value'")
                if (scale < 0) {
                    throw ClearTermsValidationException("Invalid number format '$value'")
                }
                ClearTermsNumberFormat.Decimal(scale)
            }
            else -> throw ClearTermsValidationException("Invalid number format '$value'")
        }

    private fun parseNumberGrouping(value: String): ClearTermsNumberGrouping =
        when (value) {
            "on" -> ClearTermsNumberGrouping.ON
            "off" -> ClearTermsNumberGrouping.OFF
            else -> throw ClearTermsValidationException("Invalid number grouping '$value'")
        }

    private fun parseType(value: String): ClearTermsPlaceholderType =
        when (value) {
            "string" -> ClearTermsPlaceholderType.STRING
            "number" -> ClearTermsPlaceholderType.NUMBER
            "duration" -> ClearTermsPlaceholderType.DURATION
            "time" -> ClearTermsPlaceholderType.TIME
            "weekday" -> ClearTermsPlaceholderType.WEEKDAY
            else -> throw ClearTermsValidationException("Unsupported placeholder type")
        }

    private companion object {
        private val supportedNumberOptionKeys = setOf("format", "grouping")
    }
}
