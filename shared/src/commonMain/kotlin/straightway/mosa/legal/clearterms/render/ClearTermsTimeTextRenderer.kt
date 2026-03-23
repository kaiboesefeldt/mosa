package straightway.mosa.legal.clearterms.render

import straightway.mosa.legal.clearterms.ClearTermsLocale
import straightway.mosa.legal.clearterms.ClearTermsTimeValue
import straightway.mosa.legal.clearterms.ClearTermsTimeZoneDisplayConverter
import straightway.mosa.legal.clearterms.ClearTermsValidationException
import straightway.mosa.legal.clearterms.parse.ClearTermsScalarStringNormalizer

class ClearTermsTimeTextRenderer(
    private val scalarStringNormalizer: ClearTermsScalarStringNormalizer = ClearTermsScalarStringNormalizer(),
    private val timeValueParser: ClearTermsTimeValueParser = ClearTermsTimeValueParser(),
    private val timeZoneDisplayConverter: ClearTermsTimeZoneDisplayConverter = ClearTermsTimeZoneDisplayConverter.NoOp,
) {
    fun renderTimeValue(
        value: String,
        locale: ClearTermsLocale,
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
        locale: ClearTermsLocale,
    ): String {
        val normalized = scalarStringNormalizer.normalize(value).lowercase()
        return locale.weekdays[normalized] ?: normalized
    }

    private fun renderDatePattern(
        parts: ClearTermsTimeValue,
        locale: ClearTermsLocale,
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
                throw ClearTermsValidationException("Unsupported time pattern")
            }
        }
    }

    private fun renderOverflowDate(
        parts: ClearTermsTimeValue,
        locale: ClearTermsLocale,
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
