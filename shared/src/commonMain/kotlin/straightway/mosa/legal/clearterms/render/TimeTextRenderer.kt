package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.locale.Locale
import straightway.mosa.legal.clearterms.parse.ScalarStringNormalizer
import straightway.mosa.legal.clearterms.render.TimeValue
import straightway.mosa.legal.clearterms.render.TimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.validation.ValidationException

class TimeTextRenderer(
    private val scalarStringNormalizer: ScalarStringNormalizer = ScalarStringNormalizer(),
    private val timeValueParser: TimeValueParser = TimeValueParser(),
    private val timeZoneDisplayConverter: TimeZoneDisplayConverter = TimeZoneDisplayConverter.NoOp,
) {
    fun renderTimeValue(
        value: String,
        locale: Locale,
    ): String {
        val normalized = scalarStringNormalizer.normalize(value)
        val parsed = timeValueParser.parse(normalized)
        val display = timeZoneDisplayConverter.convert(normalized, parsed)
        val parts =
            if (display == null) {
                parsed
            } else {
                parsed.copy(
                    year = display.year,
                    month = display.month,
                    day = display.day,
                    time = display.time,
                    isOverflow = false,
                )
            }
        val hasTime = parts.time != null
        val dateText = renderDatePattern(parts, locale)
        return if (hasTime) {
            "$dateText ${locale.atWord} ${parts.time}"
        } else {
            dateText
        }
    }

    fun renderWeekday(
        value: String,
        locale: Locale,
    ): String {
        val normalized = scalarStringNormalizer.normalize(value).lowercase()
        return locale.weekdays[normalized] ?: normalized
    }

    private fun renderDatePattern(
        parts: TimeValue,
        locale: Locale,
    ): String {
        val year = parts.year
        val month = parts.month
        val day = parts.day
        val overflow = parts.isOverflow
        return when {
            year != null && month != null && day != null && !overflow -> {
                locale.onFullDate(locale.monthName(month), day, year)
            }

            year != null && month != null && day == null -> {
                locale.inMonthYear(locale.monthName(month), year)
            }

            year != null && month == null && day != null && !overflow -> {
                locale.onDayOfEveryMonthInYear(day, year)
            }

            year != null && month == null && day == null -> {
                locale.inYear(year)
            }

            year == null && month != null && day != null && !overflow -> {
                locale.onMonthDayEveryYear(locale.monthName(month), day)
            }

            year == null && month != null && day == null -> {
                locale.everyDayInMonth(locale.monthName(month))
            }

            year == null && month == null && day != null && !overflow -> {
                locale.onDayOfEveryMonth(day)
            }

            year == null && month == null && day == null -> {
                locale.everyDay()
            }

            overflow -> {
                renderOverflowDate(parts, locale)
            }

            else -> {
                throw ValidationException("Unsupported time pattern")
            }
        }
    }

    private fun renderOverflowDate(
        parts: TimeValue,
        locale: Locale,
    ): String {
        val year = parts.year
        val month = parts.month
        return when {
            year != null && month != null -> locale.onLastDayOfMonth(locale.monthName(month), year)
            year == null && month != null -> locale.onLastDayOfMonthEveryYear(locale.monthName(month))
            year != null && month == null -> locale.onLastDayOfEveryMonthInYear(year)
            else -> locale.onLastDayOfEveryMonth()
        }
    }
}
