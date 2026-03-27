package straightway.mosa.legal.clearterms.placeholder

import straightway.mosa.legal.clearterms.validation.ValidationException
import straightway.mosa.legal.clearterms.value.NumberFormat
import straightway.mosa.legal.clearterms.value.NumberGrouping
import straightway.mosa.legal.clearterms.value.NumberOptions

class PlaceholderContentParser {
    fun parse(content: String): Placeholder {
        val (main, fallback) = splitFallback(content)
        val (typeToken, keyAndOptions) = splitTypeAndContent(main)
        val type = parseType(typeToken)
        val (key, options) = splitOptions(keyAndOptions)
        validatePlaceholderKey(key)
        val numberOptions = parseNumberOptions(type, options)
        return Placeholder(type, key, fallback, numberOptions)
    }

    private fun splitTypeAndContent(main: String): Pair<String, String> {
        val parts = main.split(":", limit = 2)
        if (parts.size != 2) {
            throw ValidationException("Invalid placeholder syntax")
        }
        return parts[0] to parts[1]
    }

    private fun validatePlaceholderKey(key: String) {
        if (key.isBlank() || key.any { it.isWhitespace() }) {
            throw ValidationException("Invalid placeholder key")
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
            throw ValidationException("Fallback must not contain '??'")
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
            throw ValidationException("Invalid placeholder options")
        }
        return key to options
    }

    private fun parseNumberOptions(
        type: PlaceholderType,
        options: String?,
    ): NumberOptions? {
        if (options == null) {
            return null
        }
        if (type != PlaceholderType.NUMBER) {
            throw ValidationException("Options are only supported for number placeholders")
        }
        val values = parseOptionEntries(options)
        val unknown = values.keys.firstOrNull { it !in supportedNumberOptionKeys }
        if (unknown != null) {
            throw ValidationException("Unknown number placeholder option '$unknown'")
        }
        val format = values["format"]?.let { parseNumberFormat(it) } ?: NumberFormat.Auto
        val grouping = values["grouping"]?.let { parseNumberGrouping(it) } ?: NumberGrouping.ON
        return NumberOptions(format, grouping)
    }

    private fun parseOptionEntries(options: String): Map<String, String> {
        val entries = options.split("&")
        val values = linkedMapOf<String, String>()
        entries.forEach { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw ValidationException("Invalid number placeholder options")
            }
            if (values.containsKey(parts[0])) {
                throw ValidationException("Duplicate number placeholder option '${parts[0]}'")
            }
            values[parts[0]] = parts[1]
        }
        return values
    }

    private fun parseNumberFormat(value: String): NumberFormat =
        when {
            value == "integer" -> {
                NumberFormat.Integer
            }

            value == "auto" -> {
                NumberFormat.Auto
            }

            value.startsWith("decimal(") && value.endsWith(")") -> {
                val content = value.substringAfter("decimal(").dropLast(1)
                val scale =
                    content.toIntOrNull()
                        ?: throw ValidationException("Invalid number format '$value'")
                if (scale < 0) {
                    throw ValidationException("Invalid number format '$value'")
                }
                NumberFormat.Decimal(scale)
            }

            else -> {
                throw ValidationException("Invalid number format '$value'")
            }
        }

    private fun parseNumberGrouping(value: String): NumberGrouping =
        when (value) {
            "on" -> NumberGrouping.ON
            "off" -> NumberGrouping.OFF
            else -> throw ValidationException("Invalid number grouping '$value'")
        }

    private fun parseType(value: String): PlaceholderType =
        when (value) {
            "string" -> PlaceholderType.STRING
            "number" -> PlaceholderType.NUMBER
            "duration" -> PlaceholderType.DURATION
            "time" -> PlaceholderType.TIME
            "weekday" -> PlaceholderType.WEEKDAY
            else -> throw ValidationException("Unsupported placeholder type")
        }

    private companion object {
        private val supportedNumberOptionKeys = setOf("format", "grouping")
    }
}
