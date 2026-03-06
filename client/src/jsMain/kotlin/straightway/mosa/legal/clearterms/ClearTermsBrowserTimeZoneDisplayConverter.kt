package straightway.mosa.legal.clearterms

import kotlin.js.Date

class ClearTermsBrowserTimeZoneDisplayConverter : ClearTermsTimeZoneDisplayConverter {
    override fun convert(
        value: String,
        parsed: ClearTermsTimeValue,
    ): ClearTermsTimeDisplayValue? {
        if (parsed.year == null || parsed.month == null || parsed.day == null || parsed.isOverflow) {
            return null
        }
        val time = parsed.time ?: return null
        val date = Date(value)
        if (date.getTime().isNaN()) {
            return null
        }
        val localYear = date.getFullYear().toInt()
        val localMonth = date.getMonth().toInt() + 1
        val localDay = date.getDate().toInt()
        val localHour = date.getHours().toInt()
        val localMinute = date.getMinutes().toInt()
        val localSecond = date.getSeconds().toInt()
        val hasSeconds = timeWithSecondsRegex.matches(time)
        val localTime =
            if (hasSeconds) {
                "${twoDigits(localHour)}:${twoDigits(localMinute)}:${twoDigits(localSecond)}"
            } else {
                "${twoDigits(localHour)}:${twoDigits(localMinute)}"
            }
        return ClearTermsTimeDisplayValue(
            year = localYear,
            month = localMonth,
            day = localDay,
            time = localTime,
        )
    }

    private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

    private companion object {
        val timeWithSecondsRegex = Regex("""^\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}:\d{2})$""")
    }
}
