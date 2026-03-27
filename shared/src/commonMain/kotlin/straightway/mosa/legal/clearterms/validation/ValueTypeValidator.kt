package straightway.mosa.legal.clearterms.validation

import straightway.mosa.legal.clearterms.parse.ScalarStringNormalizer
import straightway.mosa.legal.clearterms.placeholder.PlaceholderType
import straightway.mosa.legal.clearterms.render.TimeValueParser
import straightway.mosa.legal.clearterms.validation.ValidationException

class ValueTypeValidator(
    private val scalarStringNormalizer: ScalarStringNormalizer = ScalarStringNormalizer(),
    private val timeValueParser: TimeValueParser = TimeValueParser(),
) {
    fun validate(
        type: PlaceholderType,
        value: Any?,
    ) {
        when (type) {
            PlaceholderType.STRING -> validateStringValue(value)
            PlaceholderType.NUMBER -> validateNumberValue(value)
            PlaceholderType.DURATION -> validateDurationValue(value)
            PlaceholderType.TIME -> validateTimeValue(value)
            PlaceholderType.WEEKDAY -> validateWeekdayValue(value)
        }
    }

    private fun validateStringValue(value: Any?) {
        if (value != null && value !is String) {
            throw ValidationException("String value must be string")
        }
    }

    private fun validateNumberValue(value: Any?) {
        if (value != null && value !is Double) {
            throw ValidationException("Number value must be numeric")
        }
    }

    private fun validateDurationValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Duration value must be string") ?: return
        if (!isValidDuration(normalized)) {
            throw ValidationException("Invalid duration value '$normalized'")
        }
    }

    private fun validateTimeValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Time value must be string") ?: return
        timeValueParser.parse(normalized)
    }

    private fun validateWeekdayValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Weekday value must be string")?.lowercase() ?: return
        if (!weekdayRegex.matches(normalized)) {
            throw ValidationException("Invalid weekday value '$normalized'")
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

    private fun normalizedOptionalString(
        value: Any?,
        typeErrorMessage: String,
    ): String? {
        if (value == null) {
            return null
        }
        val text = value as? String ?: throw ValidationException(typeErrorMessage)
        return scalarStringNormalizer.normalize(text)
    }

    private companion object {
        private val weekdayRegex = Regex("""^(mon|tue|wed|thu|fri|sat|sun)$""")
    }
}
