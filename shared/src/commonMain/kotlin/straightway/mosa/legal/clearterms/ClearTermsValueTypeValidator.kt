package straightway.mosa.legal.clearterms

class ClearTermsValueTypeValidator(
    private val scalarStringNormalizer: ClearTermsScalarStringNormalizer = ClearTermsScalarStringNormalizer(),
    private val timeValueParser: ClearTermsTimeValueParser = ClearTermsTimeValueParser(),
) {
    fun validate(
        type: ClearTermsPlaceholderType,
        value: Any?,
    ) {
        when (type) {
            ClearTermsPlaceholderType.STRING -> validateStringValue(value)
            ClearTermsPlaceholderType.NUMBER -> validateNumberValue(value)
            ClearTermsPlaceholderType.DURATION -> validateDurationValue(value)
            ClearTermsPlaceholderType.TIME -> validateTimeValue(value)
            ClearTermsPlaceholderType.WEEKDAY -> validateWeekdayValue(value)
        }
    }

    private fun validateStringValue(value: Any?) {
        if (value != null && value !is String) {
            throw ClearTermsValidationException("String value must be string")
        }
    }

    private fun validateNumberValue(value: Any?) {
        if (value != null && value !is Double) {
            throw ClearTermsValidationException("Number value must be numeric")
        }
    }

    private fun validateDurationValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Duration value must be string") ?: return
        if (!isValidDuration(normalized)) {
            throw ClearTermsValidationException("Invalid duration value '$normalized'")
        }
    }

    private fun validateTimeValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Time value must be string") ?: return
        timeValueParser.parse(normalized)
    }

    private fun validateWeekdayValue(value: Any?) {
        val normalized = normalizedOptionalString(value, "Weekday value must be string")?.lowercase() ?: return
        if (!weekdayRegex.matches(normalized)) {
            throw ClearTermsValidationException("Invalid weekday value '$normalized'")
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
        val text = value as? String ?: throw ClearTermsValidationException(typeErrorMessage)
        return scalarStringNormalizer.normalize(text)
    }

    private companion object {
        val weekdayRegex = Regex("""^(mon|tue|wed|thu|fri|sat|sun)$""")
    }
}
