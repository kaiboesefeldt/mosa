@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.render.NumberRenderer
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsNumberRendererTest {
    private val renderer = NumberRenderer()
    private val locale =
        Locale(
            language = "en",
            months = emptyList(),
            weekdays = emptyMap(),
            decimalSeparator = ".",
            groupingSeparator = ",",
            atWord = "at",
            patterns =
                Locale.Patterns(
                    onFullDate = "",
                    inMonthYear = "",
                    onDayOfEveryMonthInYear = "",
                    inYear = "",
                    onMonthDayEveryYear = "",
                    everyDayInMonth = "",
                    onDayOfEveryMonth = "",
                    everyDay = "",
                    onLastDayOfMonth = "",
                    onLastDayOfMonthEveryYear = "",
                    onLastDayOfEveryMonthInYear = "",
                    onLastDayOfEveryMonth = "",
                ),
        )

    @Test
    fun `formats non scientific decimal values unchanged`() {
        val result = renderer.format(12.34, decimalOptions(scale = 2, grouping = NumberGrouping.OFF), locale)

        assertEquals("12.34", result)
    }

    @Test
    fun `formats scientific values with positive exponent beyond digits`() {
        val result = renderer.format(1e7, decimalOptions(scale = 2, grouping = NumberGrouping.OFF), locale)

        assertEquals("10000000.00", result)
    }

    @Test
    fun `formats scientific values with negative exponent as plain decimal`() {
        val result = renderer.format(1e-7, decimalOptions(scale = 9, grouping = NumberGrouping.OFF), locale)

        assertEquals("0.000000100", result)
    }

    @Test
    fun `formats scientific values with decimal point moved into digits`() {
        val result = renderer.format(1.23e1, decimalOptions(scale = 2, grouping = NumberGrouping.OFF), locale)

        assertEquals("12.30", result)
    }

    @Test
    fun `formats scientific values with internal decimal split`() {
        val result = renderer.format(1.23456789e7, decimalOptions(scale = 2, grouping = NumberGrouping.OFF), locale)

        assertEquals("12345678.90", result)
    }

    @Test
    fun `trims trailing zeros from scientific notation in auto format`() {
        val result = renderer.format(1.0e-4, null, locale)

        assertEquals("0.0001", result)
    }

    @Test
    fun `rejects NaN number values`() {
        assertFailsWith<ValidationException> {
            renderer.format(Double.NaN, null, locale)
        }
    }

    @Test
    fun `rejects infinite number values`() {
        assertFailsWith<ValidationException> {
            renderer.format(Double.POSITIVE_INFINITY, null, locale)
        }
    }

    private fun decimalOptions(
        scale: Int,
        grouping: NumberGrouping,
    ) = NumberOptions(
        format = NumberFormat.Decimal(scale),
        grouping = grouping,
    )
}
