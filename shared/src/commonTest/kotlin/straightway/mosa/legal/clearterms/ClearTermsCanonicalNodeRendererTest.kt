package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsCanonicalNodeRendererTest {
    private val canonicalizer = ClearTermsCanonicalNodeRenderer()

    @Test
    fun `sorts keys lexicographically at all levels`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "b" to ClearTermsJsonNode.ScalarNode.StringNode("two"),
                    "a" to
                        ClearTermsJsonNode.ObjectNode(
                            mapOf(
                                "d" to ClearTermsJsonNode.ScalarNode.StringNode("four"),
                                "c" to ClearTermsJsonNode.ScalarNode.StringNode("three"),
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
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "list" to
                        ClearTermsJsonNode.ArrayNode(
                            listOf(
                                ClearTermsJsonNode.ScalarNode.StringNode("first"),
                                ClearTermsJsonNode.ScalarNode.StringNode("second"),
                                ClearTermsJsonNode.ScalarNode.StringNode("third"),
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
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "items" to
                        ClearTermsJsonNode.ArrayNode(
                            listOf(
                                ClearTermsJsonNode.ObjectNode(
                                    mapOf(
                                        "id" to ClearTermsJsonNode.ScalarNode.StringNode("one"),
                                        "value" to ClearTermsJsonNode.ScalarNode.NumberNode(1.0),
                                    ),
                                ),
                                ClearTermsJsonNode.ObjectNode(
                                    mapOf(
                                        "id" to ClearTermsJsonNode.ScalarNode.StringNode("two"),
                                        "value" to ClearTermsJsonNode.ScalarNode.NumberNode(2.5),
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
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "value" to ClearTermsJsonNode.ScalarNode.NullNode,
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"value\":null}", result)
    }

    @Test
    fun `renders numbers without underscores`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "integer" to ClearTermsJsonNode.ScalarNode.NumberNode(10.0),
                    "decimal" to ClearTermsJsonNode.ScalarNode.NumberNode(12.75),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"decimal\":12.75,\"integer\":10}", result)
    }

    @Test
    fun `escapes strings according to rules`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "text" to ClearTermsJsonNode.ScalarNode.StringNode("\" \\ \n \r \t \u0007 end"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"\\\" \\\\ \\n \\r \\t \\u0007 end\"}", result)
    }

    @Test
    fun `escapes backspace and formfeed`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "text" to ClearTermsJsonNode.ScalarNode.StringNode("a\b\u000c b"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"a\\b\\f b\"}", result)
    }

    @Test
    fun `rejects non finite numbers`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "value" to ClearTermsJsonNode.ScalarNode.NumberNode(Double.POSITIVE_INFINITY),
                ),
            )

        assertFailsWith<ClearTermsValidationException> {
            canonicalizer.render(root)
        }
    }

    @Test
    fun `renders empty collections`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "emptyObject" to ClearTermsJsonNode.ObjectNode(emptyMap()),
                    "emptyArray" to ClearTermsJsonNode.ArrayNode(emptyList()),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"emptyArray\":[],\"emptyObject\":{}}", result)
    }

    @Test
    fun `renders empty root object`() {
        val root = ClearTermsJsonNode.ObjectNode(emptyMap())

        val result = canonicalizer.render(root)

        assertEquals("{}", result)
    }

    @Test
    fun `escapes low control characters using unicode`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "text" to ClearTermsJsonNode.ScalarNode.StringNode("a\u0001\u001f b"),
                ),
            )

        val result = canonicalizer.render(root)

        assertEquals("{\"text\":\"a\\u0001\\u001f b\"}", result)
    }

    @Test
    fun `rejects NaN numbers`() {
        val root =
            ClearTermsJsonNode.ObjectNode(
                mapOf(
                    "value" to ClearTermsJsonNode.ScalarNode.NumberNode(Double.NaN),
                ),
            )

        assertFailsWith<ClearTermsValidationException> {
            canonicalizer.render(root)
        }
    }
}
