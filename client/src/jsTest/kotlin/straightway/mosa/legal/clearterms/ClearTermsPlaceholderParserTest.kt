package straightway.mosa.legal.clearterms

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
        assertEquals("!", (segments[2] as ClearTermsPlaceholderParser.Segment.Literal).text)
    }

    @Test
    fun `parses placeholder with fallback`() {
        val segments = parser.parse("Limit {{number:max_records??a limited number}}.")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals(ClearTermsPlaceholderType.NUMBER, placeholder.type)
        assertEquals("max_records", placeholder.key)
        assertEquals("a limited number", placeholder.fallback)
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
    fun `rejects fallback on string placeholders`() {
        assertFailsWith<ClearTermsValidationException> {
            parser.parse("Hello {{string:name??fallback}}")
        }
    }

    @Test
    fun `allows fallback containing colons`() {
        val segments = parser.parse("Time {{time:start_time??12:00}}")

        val placeholder = (segments[1] as ClearTermsPlaceholderParser.Segment.Placeholder).placeholder
        assertEquals("start_time", placeholder.key)
        assertEquals("12:00", placeholder.fallback)
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
}
