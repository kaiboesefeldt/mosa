package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsCanonicalYamlTest {
    private val canonicalizer = ClearTermsCanonicalYaml()

    @Test
    fun `sorts keys lexicographically at all levels`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "b" to ClearTermsYamlNode.ScalarNode.StringNode("two"),
                "a" to ClearTermsYamlNode.MapNode(
                    mapOf(
                        "d" to ClearTermsYamlNode.ScalarNode.StringNode("four"),
                        "c" to ClearTermsYamlNode.ScalarNode.StringNode("three"),
                    ),
                ),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        val expected = listOf(
            "\"a\":",
            "  \"c\": \"three\"",
            "  \"d\": \"four\"",
            "\"b\": \"two\"",
        ).joinToString("\n")
        assertEquals(expected, result)
    }

    @Test
    fun `preserves sequence order`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "list" to ClearTermsYamlNode.SequenceNode(
                    listOf(
                        ClearTermsYamlNode.ScalarNode.StringNode("first"),
                        ClearTermsYamlNode.ScalarNode.StringNode("second"),
                        ClearTermsYamlNode.ScalarNode.StringNode("third"),
                    ),
                ),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        val expected = listOf(
            "\"list\":",
            "  - \"first\"",
            "  - \"second\"",
            "  - \"third\"",
        ).joinToString("\n")
        assertEquals(expected, result)
    }

    @Test
    fun `renders nested sequences and maps`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "items" to ClearTermsYamlNode.SequenceNode(
                    listOf(
                        ClearTermsYamlNode.MapNode(
                            mapOf(
                                "id" to ClearTermsYamlNode.ScalarNode.StringNode("one"),
                                "value" to ClearTermsYamlNode.ScalarNode.NumberNode(1.0),
                            ),
                        ),
                        ClearTermsYamlNode.MapNode(
                            mapOf(
                                "id" to ClearTermsYamlNode.ScalarNode.StringNode("two"),
                                "value" to ClearTermsYamlNode.ScalarNode.NumberNode(2.5),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        val expected = listOf(
            "\"items\":",
            "  -",
            "    \"id\": \"one\"",
            "    \"value\": 1",
            "  -",
            "    \"id\": \"two\"",
            "    \"value\": 2.5",
        ).joinToString("\n")
        assertEquals(expected, result)
    }

    @Test
    fun `renders nulls explicitly`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "value" to ClearTermsYamlNode.ScalarNode.NullNode,
            ),
        )

        val result = canonicalizer.canonicalize(root)

        assertEquals("\"value\": null", result)
    }

    @Test
    fun `renders numbers without underscores`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "integer" to ClearTermsYamlNode.ScalarNode.NumberNode(10.0),
                "decimal" to ClearTermsYamlNode.ScalarNode.NumberNode(12.75),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        val expected = listOf(
            "\"decimal\": 12.75",
            "\"integer\": 10",
        ).joinToString("\n")
        assertEquals(expected, result)
    }

    @Test
    fun `escapes strings according to rules`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "text" to ClearTermsYamlNode.ScalarNode.StringNode("\" \\ \n \r \t \u0007 end"),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        assertEquals("\"text\": \"\\\" \\\\ \\n \\r \\t \\u0007 end\"", result)
    }

    @Test
    fun `rejects non finite numbers`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "value" to ClearTermsYamlNode.ScalarNode.NumberNode(Double.POSITIVE_INFINITY),
            ),
        )

        assertFailsWith<ClearTermsValidationException> {
            canonicalizer.canonicalize(root)
        }
    }

    @Test
    fun `renders empty collections`() {
        val root = ClearTermsYamlNode.MapNode(
            mapOf(
                "emptyMap" to ClearTermsYamlNode.MapNode(emptyMap()),
                "emptyList" to ClearTermsYamlNode.SequenceNode(emptyList()),
            ),
        )

        val result = canonicalizer.canonicalize(root)

        val expected = listOf(
            "\"emptyList\":",
            "\"emptyMap\":",
        ).joinToString("\n")
        assertEquals(expected, result)
    }
}
