package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderParser
import straightway.mosa.legal.clearterms.placeholder.ClearTermsPlaceholderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsPlaceholderParserTest {
    private val parser = ClearTermsPlaceholderParser()

    @Test
    fun `parses single placeholder`() {
        val segments = parser.parse("Hello {{string:name}}!")

        assertEquals(3, segments.size)
        assertEquals("Hello ", (segments[0] as ClearTermsPlaceholderParser.Segment.Literal).text)
        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.STRING, placeholder.type)
        assertEquals("name", placeholder.key)
        assertEquals(null, placeholder.fallback)
        assertEquals(null, placeholder.numberOptions)
        assertEquals("!", (segments[2] as ClearTermsPlaceholderParser.Segment.Literal).text)
    }

    @Test
    fun `parses placeholder with fallback`() {
        val segments = parser.parse("Limit {{number:max_records??a limited number}}.")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.NUMBER, placeholder.type)
        assertEquals("max_records", placeholder.key)
        assertEquals("a limited number", placeholder.fallback)
        assertEquals(null, placeholder.numberOptions)
    }

    @Test
    fun `parses multiple placeholders`() {
        val segments = parser.parse("{{string:one}} and {{string:two}}")

        assertEquals(3, segments.size)
        assertEquals("one", (segments[0] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.key)
        assertEquals(" and ", (segments[1] as ClearTermsPlaceholderParser.Segment.Literal).text)
        assertEquals("two", (segments[2] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.key)
    }

    @Test
    fun `handles escaped braces`() {
        val segments = parser.parse("Literal \\{{ and \\}} then {{string:name}}")

        assertEquals(2, segments.size)
        assertEquals("Literal {{ and }} then ", (segments[0] as ClearTermsPlaceholderParser.Segment.Literal).text)
        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals("name", placeholder.key)
        assertEquals(null, placeholder.numberOptions)
    }

    @Test
    fun `rejects unterminated placeholder`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{string:name")
        }
    }

    @Test
    fun `rejects invalid placeholder syntax`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{stringname}}")
        }
    }

    @Test
    fun `rejects unknown placeholder type`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{boolean:flag}}")
        }
    }

    @Test
    fun `rejects empty key`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{string:}}")
        }
    }

    @Test
    fun `rejects whitespace in key`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{string:bad key}}")
        }
    }

    @Test
    fun `parses fallback on string placeholders`() {
        val segments = parser.parse("Hello {{string:name??fallback}}")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.STRING, placeholder.type)
        assertEquals("name", placeholder.key)
        assertEquals("fallback", placeholder.fallback)
    }

    @Test
    fun `allows fallback containing colons`() {
        val segments = parser.parse("Time {{time:start_time??12:00}}")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals("start_time", placeholder.key)
        assertEquals("12:00", placeholder.fallback)
        assertEquals(null, placeholder.numberOptions)
    }

    @Test
    fun `handles escaped braces without placeholders`() {
        val segments = parser.parse("\\{{ only literal \\}}")

        assertEquals(1, segments.size)
        assertEquals("{{ only literal }}", (segments[0] as ClearTermsPlaceholderParser.Segment.Literal).text)
    }

    @Test
    fun `preserves text around placeholders`() {
        val segments = parser.parse("A {{string:one}} B {{number:two??2}} C")

        assertEquals("A ", (segments[0] as ClearTermsPlaceholderParser.Segment.Literal).text)
        assertEquals(" B ", (segments[2] as ClearTermsPlaceholderParser.Segment.Literal).text)
        assertEquals(" C", (segments[4] as ClearTermsPlaceholderParser.Segment.Literal).text)
    }

    @Test
    fun `skips escaped placeholder ends`() {
        val segments = parser.parse("Value {{number:limit??\\}}}} end")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals("limit", placeholder.key)
        assertEquals("\\}}", placeholder.fallback)
        assertEquals(null, placeholder.numberOptions)
    }

    @Test
    fun `parses number placeholder options`() {
        val segments = parser.parse("Total {{number:price?format=decimal(2)&grouping=off}}.")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        val options = placeholder.numberOptions
        assertEquals(ClearTermsNumberFormat.Decimal(2), options?.format)
        assertEquals(ClearTermsNumberGrouping.OFF, options?.grouping)
    }

    @Test
    fun `rejects options for non number placeholders`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{string:name?format=integer}}")
        }
    }

    @Test
    fun `rejects unknown number options`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?scale=2}}")
        }
    }

    @Test
    fun `rejects invalid number format option`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?format=decimal(x)}}")
        }
    }

    @Test
    fun `rejects fallback containing double question marks`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price??too??much}}")
        }
    }

    @Test
    fun `rejects blank placeholder options`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?}}")
        }
    }

    @Test
    fun `rejects malformed number options`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?format}}")
        }
    }

    @Test
    fun `rejects duplicate number options`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?format=integer&format=auto}}")
        }
    }

    @Test
    fun `parses default format when only grouping option is provided`() {
        val segments = parser.parse("Total {{number:price?grouping=off}}")

        val options = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.numberOptions
        assertEquals(ClearTermsNumberFormat.Auto, options?.format)
        assertEquals(ClearTermsNumberGrouping.OFF, options?.grouping)
    }

    @Test
    fun `parses default grouping when only format option is provided`() {
        val segments = parser.parse("Total {{number:price?format=integer}}")

        val options = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.numberOptions
        assertEquals(ClearTermsNumberFormat.Integer, options?.format)
        assertEquals(ClearTermsNumberGrouping.ON, options?.grouping)
    }

    @Test
    fun `parses explicit auto format`() {
        val segments = parser.parse("Total {{number:price?format=auto&grouping=off}}")

        val options = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.numberOptions
        assertEquals(ClearTermsNumberFormat.Auto, options?.format)
        assertEquals(ClearTermsNumberGrouping.OFF, options?.grouping)
    }

    @Test
    fun `parses explicit grouping on`() {
        val segments = parser.parse("Total {{number:price?format=integer&grouping=on}}")

        val options = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder.numberOptions
        assertEquals(ClearTermsNumberFormat.Integer, options?.format)
        assertEquals(ClearTermsNumberGrouping.ON, options?.grouping)
    }

    @Test
    fun `rejects negative decimal scale`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?format=decimal(-1)}}")
        }
    }

    @Test
    fun `rejects invalid number grouping option`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Total {{number:price?grouping=maybe}}")
        }
    }

    @Test
    fun `parses duration placeholder type`() {
        val segments = parser.parse("Expires after {{duration:term}}.")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.DURATION, placeholder.type)
    }

    @Test
    fun `parses weekday placeholder type`() {
        val segments = parser.parse("Occurs on {{weekday:day}}.")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.WEEKDAY, placeholder.type)
    }
}
