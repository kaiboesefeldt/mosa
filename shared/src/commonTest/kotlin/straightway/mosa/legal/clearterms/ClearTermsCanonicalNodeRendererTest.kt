@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.canonical.CanonicalNodeRenderer
import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsCanonicalNodeRendererTest {
    private val canonicalizer = CanonicalNodeRenderer()

    @Test
    fun `sorts keys lexicographically at all levels`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "b" to JsonNode.ScalarNode.StringNode("two"),
                    "a" to
                        JsonNode.ObjectNode(
                            mapOf(
                                "d" to JsonNode.ScalarNode.StringNode("four"),
                                "c" to JsonNode.ScalarNode.StringNode("three"),
                            ),
                        ),
                ),
            )

        val result = canonicalizer.render(root)

        val expected = "{\"a\":{\"c\":\"three\",\"d\":\"four\"},\"b\":\"two\"}"
        assertEquals(expected, result)
    }

    @Test
    fun `preserves array order`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "list" to
                        JsonNode.ArrayNode(
                            listOf(
                                JsonNode.ScalarNode.StringNode("first"),
                                JsonNode.ScalarNode.StringNode("second"),
                                JsonNode.ScalarNode.StringNode("third"),
                            ),
                        ),
                ),
            )

        val result = canonicalizer.render(root)

        val expected = "{\"list\":[\"first\",\"second\",\"third\"]}"
        assertEquals(expected, result)
    }

    @Test
    fun `renders nested arrays and objects`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "items" to
                        JsonNode.ArrayNode(
                            listOf(
                                JsonNode.ObjectNode(
                                    mapOf(
                                        "id" to JsonNode.ScalarNode.StringNode("one"),
                                        "value" to JsonNode.ScalarNode.NumberNode(1.0),
                                    ),
                                ),
                                JsonNode.ObjectNode(
                                    mapOf(
                                        "id" to JsonNode.ScalarNode.StringNode("two"),
                                        "value" to JsonNode.ScalarNode.NumberNode(2.5),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        val result = canonicalizer.render(root)

        val expected = "{\"items\":[{\"id\":\"one\",\"value\":1},{\"id\":\"two\",\"value\":2.5}]}"
        assertEquals(expected, result)
    }

    @Test
    fun `renders nulls explicitly`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "value" to JsonNode.ScalarNode.NullNode,
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"value\":null}", result)
    }

    @Test
    fun `renders numbers without underscores`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "integer" to JsonNode.ScalarNode.NumberNode(10.0),
                    "decimal" to JsonNode.ScalarNode.NumberNode(12.75),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"decimal\":12.75,\"integer\":10}", result)
    }

    @Test
    fun `escapes strings according to rules`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "text" to JsonNode.ScalarNode.StringNode("\" \\ \n \r \t \u0007 end"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"\\\" \\\\ \\n \\r \\t \\u0007 end\"}", result)
    }

    @Test
    fun `escapes backspace and formfeed`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "text" to JsonNode.ScalarNode.StringNode("a\b\u000c b"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"a\\b\\f b\"}", result)
    }

    @Test
    fun `rejects non finite numbers`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "value" to JsonNode.ScalarNode.NumberNode(Double.POSITIVE_INFINITY),
                ),
            )

        assertFailsWith<ValidationException> {
            canonicalizer.render(root)
        }
    }

    @Test
    fun `renders empty collections`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "emptyObject" to JsonNode.ObjectNode(emptyMap()),
                    "emptyArray" to JsonNode.ArrayNode(emptyList()),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"emptyArray\":[],\"emptyObject\":{}}", result)
    }

    @Test
    fun `renders empty root object`() {
        val root = JsonNode.ObjectNode(emptyMap())

        val result = canonicalizer.render(root)

        assertEquals("{}", result)
    }

    @Test
    fun `escapes low control characters using unicode`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "text" to JsonNode.ScalarNode.StringNode("a\u0001\u001f b"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"a\\u0001\\u001f b\"}", result)
    }

    @Test
    fun `rejects NaN numbers`() {
        val root =
            JsonNode.ObjectNode(
                mapOf(
                    "value" to JsonNode.ScalarNode.NumberNode(Double.NaN),
                ),
            )

        assertFailsWith<ValidationException> {
            canonicalizer.render(root)
        }
    }
}
