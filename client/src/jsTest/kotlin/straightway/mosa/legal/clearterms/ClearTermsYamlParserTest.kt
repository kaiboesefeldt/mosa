package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsYamlParserTest {
    private val parser = ClearTermsYamlParser()

    @Test
    fun `parses mapping with scalars sequences and nested maps`() {
        val yaml = """
root:
  string: "value"
  number: 12.5
  nullValue: null
  list:
    - one
    - two
  nested:
    key: "nested-value"
""".trimIndent()

        val root = parser.parse(yaml)
        val rootNode = root.entries.getValue("root") as ClearTermsYamlNode.MapNode

        val stringNode = rootNode.entries.getValue("string") as ClearTermsYamlNode.ScalarNode.StringNode
        val numberNode = rootNode.entries.getValue("number") as ClearTermsYamlNode.ScalarNode.NumberNode
        assertTrue(rootNode.entries.getValue("nullValue") is ClearTermsYamlNode.ScalarNode.NullNode)
        val listNode = rootNode.entries.getValue("list") as ClearTermsYamlNode.SequenceNode
        val nestedNode = rootNode.entries.getValue("nested") as ClearTermsYamlNode.MapNode

        assertEquals("value", stringNode.value)
        assertEquals(12.5, numberNode.value)
        assertEquals(2, listNode.items.size)
        assertEquals("nested-value", (nestedNode.entries.getValue("key") as ClearTermsYamlNode.ScalarNode.StringNode).value)
    }

    @Test
    fun `rejects root that is not a mapping`() {
        val yaml = """
- one
- two
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `rejects duplicate keys`() {
        val yaml = """
root:
  key: "one"
  key: "two"
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `rejects non string mapping keys`() {
        val yaml = """
? [a, b]
: value
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `rejects aliases`() {
        val yaml = """
root:
  key: &anchor "value"
  ref: *anchor
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `rejects anchors without aliases`() {
        val yaml = """
root:
  key: &anchor "value"
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `rejects merge keys`() {
        val yaml = """
base: &base
  key: value
merged:
  <<: *base
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }

    @Test
    fun `parses empty mapping`() {
        val yaml = """
root: {}
""".trimIndent()

        val root = parser.parse(yaml)
        val rootNode = root.entries.getValue("root") as ClearTermsYamlNode.MapNode

        assertEquals(0, rootNode.entries.size)
    }

    @Test
    fun `rejects invalid yaml`() {
        val yaml = """
root:
  - broken: [
""".trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            parser.parse(yaml)
        }
    }
}
