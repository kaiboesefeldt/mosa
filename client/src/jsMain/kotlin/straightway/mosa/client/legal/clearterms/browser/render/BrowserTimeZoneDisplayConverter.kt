package straightway.mosa.client.legal.clearterms.browser.render

import straightway.mosa.legal.clearterms.render.TimeDisplayValue
import straightway.mosa.legal.clearterms.render.TimeValue
import straightway.mosa.legal.clearterms.render.TimeZoneDisplayConverter
import kotlin.js.Date

class BrowserTimeZoneDisplayConverter : TimeZoneDisplayConverter {
    override fun convert(
        value: String,
        parsed: TimeValue,
    ): TimeDisplayValue? {
        if (parsed.year == null || parsed.month == null || parsed.day == null || parsed.isOverflow) {
            return null
        }
        val time = parsed.time ?: return null
        val date = Date(value)
        if (date.getTime().isNaN()) {
            return null
        }
        val localYear = date.getFullYear()
        val localMonth = date.getMonth() + 1
        val localDay = date.getDate()
        val localHour = date.getHours()
        val localMinute = date.getMinutes()
        val localSecond = date.getSeconds()
        val hasSeconds = timeWithSecondsRegex.matches(time)
        val localTime =
            if (hasSeconds) {
                "${twoDigits(localHour)}:${twoDigits(localMinute)}:${twoDigits(localSecond)}"
            } else {
                "${twoDigits(localHour)}:${twoDigits(localMinute)}"
            }
        return TimeDisplayValue(
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