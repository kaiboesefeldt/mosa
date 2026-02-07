package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsCanonicalJsonTest {
    private val canonicalizer = ClearTermsCanonicalJson()

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

        val result = canonicalizer.canonicalize(root)

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

        val result = canonicalizer.canonicalize(root)

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

        val result = canonicalizer.canonicalize(root)

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

        val result = canonicalizer.canonicalize(root)

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

        val result = canonicalizer.canonicalize(root)

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

        val result = canonicalizer.canonicalize(root)

        assertEquals("{\"text\":\"\\\" \\\\ \\n \\r \\t \\u0007 end\"}", result)
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
            canonicalizer.canonicalize(root)
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

        val result = canonicalizer.canonicalize(root)

        assertEquals("{\"emptyArray\":[],\"emptyObject\":{}}", result)
    }
}
