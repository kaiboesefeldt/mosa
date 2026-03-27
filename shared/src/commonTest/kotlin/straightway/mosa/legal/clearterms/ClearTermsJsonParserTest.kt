@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsJsonParserTest {
    private val parser = JsonParser()

    @Test
    fun `parses object with scalars arrays and nested objects`() {
        val json =
            """
            {
              "root": {
                "string": "value",
                "number": 12.5,
                "nullValue": null,
                "list": ["one", "two"],
                "nested": { "key": "nested-value" }
              }
            }
            """.trimIndent()

        val root = parser.parse(json)
        val rootNode = root.entries.getValue("root") as JsonNode.ObjectNode

        val stringNode = rootNode.entries.getValue("string") as JsonNode.ScalarNode.StringNode
        val numberNode = rootNode.entries.getValue("number") as JsonNode.ScalarNode.NumberNode
        assertTrue(rootNode.entries.getValue("nullValue") is JsonNode.ScalarNode.NullNode)
        val listNode = rootNode.entries.getValue("list") as JsonNode.ArrayNode
        val nestedNode = rootNode.entries.getValue("nested") as JsonNode.ObjectNode

        assertEquals("value", stringNode.value)
        assertEquals(12.5, numberNode.value)
        assertEquals(2, listNode.items.size)
        assertEquals("nested-value", (nestedNode.entries.getValue("key") as JsonNode.ScalarNode.StringNode).value)
    }

    @Test
    fun `rejects root that is not an object`() {
        val json =
            """
            ["one", "two"]
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects duplicate keys`() {
        val json =
            """
            { "root": { "key": "one", "key": "two" } }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `parses boolean values`() {
        val json =
            """
            { "root": true, "other": false }
            """.trimIndent()

        val root = parser.parse(json)
        val trueValue = root.entries.getValue("root") as JsonNode.ScalarNode.BooleanNode
        val falseValue = root.entries.getValue("other") as JsonNode.ScalarNode.BooleanNode

        assertEquals(true, trueValue.value)
        assertEquals(false, falseValue.value)
    }

    @Test
    fun `rejects invalid json`() {
        val json =
            """
            { "root": [ }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects unterminated array right after opening bracket`() {
        val json =
            """
            { "root": [
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `parses empty object`() {
        val json =
            """
            { "root": {} }
            """.trimIndent()

        val root = parser.parse(json)
        val rootNode = root.entries.getValue("root") as JsonNode.ObjectNode

        assertEquals(0, rootNode.entries.size)
    }

    @Test
    fun `parses unicode escapes`() {
        val json =
            """
            { "root": "\u0041" }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.StringNode

        assertEquals("A", value.value)
    }

    @Test
    fun `parses unicode escapes with hex letters`() {
        val json =
            """
            { "root": "\u00af" }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.StringNode

        assertEquals("\u00af", value.value)
    }

    @Test
    fun `parses escaped newline`() {
        val json =
            """
            { "root": "line1\nline2" }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.StringNode

        assertEquals("line1\nline2", value.value)
    }

    @Test
    fun `rejects invalid escape`() {
        val json =
            """
            { "root": "\x" }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects trailing content after value`() {
        val json =
            """
            { "root": {} } trailing
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects empty input`() {
        assertFailsWith<ValidationException> {
            parser.parse("")
        }
    }

    @Test
    fun `parses negative numbers`() {
        val json =
            """
            { "root": -12 }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.NumberNode

        assertEquals(-12.0, value.value)
    }

    @Test
    fun `rejects negative sign without digits`() {
        val json =
            """
            { "root": - }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects root negative sign without digits`() {
        assertFailsWith<ValidationException> {
            parser.parse("-")
        }
    }

    @Test
    fun `parses zero`() {
        val json =
            """
            { "root": 0 }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.NumberNode

        assertEquals(0.0, value.value)
    }

    @Test
    fun `rejects unterminated object`() {
        val json =
            """
            { "root": { "a": 1
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects object missing comma`() {
        val json =
            """
            { "root": { "a": 1 "b": 2 } }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects object missing colon`() {
        val json =
            """
            { "root" 1 }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects unterminated array`() {
        val json =
            """
            { "root": [ 1
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects array missing comma`() {
        val json =
            """
            { "root": [1 2] }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects control characters in strings`() {
        val control = 0x01.toChar()
        val json = "{ \"root\": \"$control\" }"

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects unterminated string`() {
        val json =
            """
            { "root": "value }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects unterminated escape`() {
        val json = "{ \"root\": \"value\\"

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `parses standard escape sequences`() {
        val json = "{ \"root\": \"\\\" \\\\ \\/ \\b \\f \\n \\r \\t\" }"

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as JsonNode.ScalarNode.StringNode

        assertEquals("\" \\ / \b \u000c \n \r \t", value.value)
    }

    @Test
    fun `rejects invalid unicode escape length`() {
        val json = "{\"root\":\"\\u\"}"

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects invalid unicode escape characters`() {
        val json = "{ \"root\": \"\\uZZZZ\" }"

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects numbers without digits after exponent`() {
        val json =
            """
            { "root": 1e }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects root exponent without digits`() {
        assertFailsWith<ValidationException> {
            parser.parse("1e")
        }
    }

    @Test
    fun `parses exponent with plus and minus`() {
        val plusJson = """{ "root": 1e+2 }"""
        val minusJson = """{ "root": 1e-2 }"""

        val plusValue = parser.parse(plusJson).entries.getValue("root") as JsonNode.ScalarNode.NumberNode
        val minusValue = parser.parse(minusJson).entries.getValue("root") as JsonNode.ScalarNode.NumberNode

        assertEquals(100.0, plusValue.value)
        assertEquals(0.01, minusValue.value)
    }

    @Test
    fun `rejects incomplete object key`() {
        val json =
            """
            {
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects invalid null literal`() {
        val json =
            """
            { "root": nul }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects numbers with non ascii digits`() {
        val json = "{ \"root\": \u0661 }"

        assertFailsWith<ValidationException> {
            parser.parse(json)
        }
    }
}
