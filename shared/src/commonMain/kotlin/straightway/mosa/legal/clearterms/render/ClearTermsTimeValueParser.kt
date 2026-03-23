package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.ClearTermsTimeValue
import straightway.mosa.legal.clearterms.ClearTermsValidationException

class ClearTermsTimeValueParser {
    fun parse(value: String): ClearTermsTimeValue {
        ensureValidTimeSyntax(value)
        val datePart = value.substringBefore("T")
        val timePart = value.substringAfter("T", "")
        val dateSegments = datePart.split("-", limit = 3)
        val year = parseYear(dateSegments[0])
        val month = parseMonth(dateSegments[1], value)
        val day = parseDay(dateSegments[2], value)
        val time = if (timePart.isEmpty()) null else parseTimeOfDay(timePart, value)
        val overflow = isOverflowDay(year, month, day)
        return ClearTermsTimeValue(year, month, day, time, overflow)
    }

    private fun parseYear(value: String): Int? {
        if (value == "*") {
            return null
        }
        return value.toInt()
    }

    private fun parseMonth(
        value: String,
        raw: String,
    ): Int? {
        if (value == "*") {
            return null
        }
        val month = value.toInt()
        ensureInRange(month, 1, 12, raw)
        return month
    }

    private fun parseDay(
        value: String,
        raw: String,
    ): Int? {
        if (value == "*") {
            return null
        }
        val day = value.toInt()
        ensureInRange(day, 1, 31, raw)
        return day
    }

    private fun parseTimeOfDay(
        value: String,
        raw: String,
    ): String {
        val zoneIndex = findZoneIndex(value)
        val timePart = value.substring(0, zoneIndex)
        val segments = timePart.split(":")
        val hour = segments[0].toInt()
        val minute = segments[1].toInt()
        val second = segments.getOrNull(2)?.toIntOrNull()
        ensureTimeComponents(hour, minute, second, raw)
        return value
    }

    private fun findZoneIndex(value: String): Int {
        if (value.endsWith("Z")) {
            return value.length - 1
        }
        val plusIndex = value.lastIndexOf('+')
        val minusIndex = value.lastIndexOf('-')
        val index = maxOf(plusIndex, minusIndex)
        return index
    }

    private fun isOverflowDay(
        year: Int?,
        month: Int?,
        day: Int?,
    ): Boolean {
        if (day == null) {
            return false
        }
        if (month == null) {
            return day == 31
        }
        val maxDay = maxDayOfMonth(year, month)
        return day > maxDay
    }

    private fun maxDayOfMonth(
        year: Int?,
        month: Int,
    ): Int =
        when (month) {
            2 -> {
                if (year != null && isLeapYear(year)) {
                    29
                } else if (year == null) {
                    29
                } else {
                    28
                }
            }

            4, 6, 9, 11 -> {
                30
            }

            else -> {
                31
            }
        }

    private fun ensureValidTimeSyntax(raw: String) {
        if (!timeRegex.matches(raw)) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
    }

    private fun ensureInRange(
        value: Int,
        min: Int,
        max: Int,
        raw: String,
    ) {
        if (value !in min..max) {
            throw ClearTermsValidationException("Invalid time value '$raw'")
        }
    }

    private fun ensureTimeComponents(
        hour: Int,
        minute: Int,
        second: Int?,
        raw: String,
    ) {
        ensureInRange(hour, 0, 23, raw)
        ensureInRange(minute, 0, 59, raw)
        if (second != null) {
            ensureInRange(second, 0, 59, raw)
        }
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private companion object {
        val timeRegex = Regex("""^(\*|\d{4})-(\*|\d{2})-(\*|\d{2})(T\d{2}:\d{2}(:\d{2})?(Z|[+-]\d{2}:\d{2}))?$""")
    }
}
