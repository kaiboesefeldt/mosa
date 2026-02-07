package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsJsonParserTest {
    private val parser = ClearTermsJsonParser()

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
        val rootNode = root.entries.getValue("root") as ClearTermsJsonNode.ObjectNode

        val stringNode = rootNode.entries.getValue("string") as ClearTermsJsonNode.ScalarNode.StringNode
        val numberNode = rootNode.entries.getValue("number") as ClearTermsJsonNode.ScalarNode.NumberNode
        assertTrue(rootNode.entries.getValue("nullValue") is ClearTermsJsonNode.ScalarNode.NullNode)
        val listNode = rootNode.entries.getValue("list") as ClearTermsJsonNode.ArrayNode
        val nestedNode = rootNode.entries.getValue("nested") as ClearTermsJsonNode.ObjectNode

        assertEquals("value", stringNode.value)
        assertEquals(12.5, numberNode.value)
        assertEquals(2, listNode.items.size)
        assertEquals("nested-value", (nestedNode.entries.getValue("key") as ClearTermsJsonNode.ScalarNode.StringNode).value)
    }

    @Test
    fun `rejects root that is not an object`() {
        val json =
            """
["one", "two"]
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects duplicate keys`() {
        val json =
            """
{ "root": { "key": "one", "key": "two" } }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects boolean values`() {
        val json =
            """
{ "root": true }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(json)
        }
    }

    @Test
    fun `rejects invalid json`() {
        val json =
            """
{ "root": [ }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
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
        val rootNode = root.entries.getValue("root") as ClearTermsJsonNode.ObjectNode

        assertEquals(0, rootNode.entries.size)
    }

    @Test
    fun `parses unicode escapes`() {
        val json =
            """
{ "root": "\u0041" }
            """.trimIndent()

        val root = parser.parse(json)
        val value = root.entries.getValue("root") as ClearTermsJsonNode.ScalarNode.StringNode

        assertEquals("A", value.value)
    }

    @Test
    fun `rejects invalid escape`() {
        val json =
            """
{ "root": "\x" }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(json)
        }
    }
}
