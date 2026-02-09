package straightway.mosa.legal.clearterms

class ClearTermsPlaceholderParser {
    fun parse(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        val buffer = StringBuilder()
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("\\{{", index) -> {
                    buffer.append("{{")
                    index += 3
                }
                text.startsWith("\\}}", index) -> {
                    buffer.append("}}")
                    index += 3
                }
                text.startsWith("{{", index) -> {
                    if (buffer.isNotEmpty()) {
                        segments += Segment.Literal(buffer.toString())
                        buffer.clear()
                    }
                    val endIndex = findPlaceholderEnd(text, index + 2)
                    if (endIndex < 0) {
                        throw ClearTermsValidationException("Unterminated placeholder")
                    }
                    val placeholderText = text.substring(index + 2, endIndex)
                    segments += Segment.Placeholder(parsePlaceholder(placeholderText))
                    index = endIndex + 2
                }
                else -> {
                    buffer.append(text[index])
                    index += 1
                }
            }
        }
        if (buffer.isNotEmpty()) {
            segments += Segment.Literal(buffer.toString())
        }
        return segments
    }

    private fun findPlaceholderEnd(
        text: String,
        startIndex: Int,
    ): Int {
        var index = startIndex
        while (index < text.length - 1) {
            if (text.startsWith("\\}}", index)) {
                index += 3
                continue
            }
            if (text.startsWith("}}", index)) {
                return index
            }
            index += 1
        }
        return -1
    }

    private fun parsePlaceholder(content: String): ClearTermsPlaceholder {
        val (main, fallback) = splitFallback(content)
        val parts = main.split(":", limit = 2)
        if (parts.size != 2) {
            throw ClearTermsValidationException("Invalid placeholder syntax")
        }
        val type = parseType(parts[0])
        val (key, options) = splitOptions(parts[1])
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            throw ClearTermsValidationException("Invalid placeholder key")
        }
        if (type == ClearTermsPlaceholderType.STRING && fallback != null) {
            throw ClearTermsValidationException("String placeholders must not define fallbacks")
        }
        val numberOptions = parseNumberOptions(type, options)
        return ClearTermsPlaceholder(type, key, fallback, numberOptions)
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
        val formatValue = values["format"]
        val format =
            if (formatValue == null) {
                ClearTermsNumberFormat.Auto
            } else {
                parseNumberFormat(formatValue)
            }
        val groupingValue = values["grouping"]
        val grouping =
            if (groupingValue == null) {
                ClearTermsNumberGrouping.ON
            } else {
                parseNumberGrouping(groupingValue)
            }
        val allowedKeys = setOf("format", "grouping")
        val unknown = values.keys.firstOrNull { it !in allowedKeys }
        if (unknown != null) {
            throw ClearTermsValidationException("Unknown number placeholder option '$unknown'")
        }
        return ClearTermsNumberOptions(format, grouping)
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

    sealed class Segment {
        data class Literal(
            val text: String,
        ) : Segment()

        data class Placeholder(
            val placeholder: ClearTermsPlaceholder,
        ) : Segment()
    }
}
