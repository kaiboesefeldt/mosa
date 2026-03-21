package straightway.mosa.legal

import straightway.mosa.legal.clearterms.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegalDocumentFactoryTest {
    private val converter = ClearTermsYamlCanonicalJsonConverter()
    private val parser =
        straightway.mosa.legal.clearterms
            .ClearTermsJsonParser()
    private val reader =
        straightway.mosa.legal.clearterms
            .ClearTermsDocumentReader()

    @Test
    fun `create fallback documents provide defaults for all placeholder types and unresolved texts`() {
        val conversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithAllPlaceholderTypesAndUnresolvedEnglishText(),
                profileChainYamls =
                    listOf(
                        syntheticProfileYaml("synthetic-default", "synthetic-baseline"),
                        syntheticProfileYaml("synthetic-mosa", "synthetic-default"),
                    ),
            )
        val factory = createLegalDocumentFactory()

        val documents = factory.createFallbackDocuments(conversion, LegalDocumentKind.Privacy)
        val fallbackProfile = reader.readProfile(parser.parse(documents.lastProfileCanonicalJson))
        val values = checkNotNull(fallbackProfile.overrides.values)
        val texts = checkNotNull(fallbackProfile.overrides.texts)

        assertEquals("mosa-privacy-fallback-service", fallbackProfile.id)
        assertEquals(0.0, (values.getValue("N_KEY") as ClearTermsJsonNode.ScalarNode.NumberNode).value)
        assertEquals("mon", (values.getValue("W_KEY") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertEquals("infinite", (values.getValue("D_KEY") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertEquals("*-*-*", (values.getValue("T_KEY") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertEquals("not specified", (values.getValue("S_KEY") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertEquals(
            "Not specified (unresolved-slot).",
            texts.getValue("en").replace.getValue("unresolved-slot"),
        )
        assertEquals(
            "Nicht angegeben (unresolved-slot-de).",
            texts.getValue("de").replace.getValue("unresolved-slot-de"),
        )
    }

    @Test
    fun `create fallback documents keeps valid operator override and omits text overrides when none are needed`() {
        val conversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithRequiredStringPlaceholder(),
                profileChainYamls = listOf(syntheticProfileYaml("synthetic-default", "synthetic-baseline")),
            )
        val factory = createLegalDocumentFactory()

        val documents =
            factory.createFallbackDocuments(
                mandatoryConversion = conversion,
                kind = LegalDocumentKind.Terms,
                operatorValuesOverride =
                    mapOf(
                        "REQ_KEY" to ClearTermsJsonNode.ScalarNode.StringNode("Provided"),
                    ),
            )
        val fallbackProfile = reader.readProfile(parser.parse(documents.lastProfileCanonicalJson))
        val values = checkNotNull(fallbackProfile.overrides.values)

        assertEquals("mosa-terms-fallback-operator", fallbackProfile.id)
        assertEquals("Provided", (values.getValue("REQ_KEY") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertNull(fallbackProfile.overrides.texts)
    }

    @Test
    fun `create fallback documents ignore invalid operator override values`() {
        val conversion =
            converter.convert(
                baselineYaml = syntheticBaselineWithRequiredStringPlaceholder(),
                profileChainYamls = listOf(syntheticProfileYaml("synthetic-default", "synthetic-baseline")),
            )
        val factory = createLegalDocumentFactory()

        val documents =
            factory.createFallbackDocuments(
                mandatoryConversion = conversion,
                kind = LegalDocumentKind.Terms,
                operatorValuesOverride =
                    mapOf(
                        "REQ_KEY" to ClearTermsJsonNode.ScalarNode.NumberNode(42.0),
                    ),
            )
        val fallbackProfile = reader.readProfile(parser.parse(documents.lastProfileCanonicalJson))

        assertTrue(checkNotNull(fallbackProfile.overrides.values).isEmpty())
    }

    @Test
    fun `extract operator values best effort keeps supported scalars only`() {
        val factory = createLegalDocumentFactory()

        val values =
            factory.extractOperatorValuesBestEffort(
                """
                kind: profile
                overrides:
                  values:
                    NAME: "Operator"
                    COUNT: 42
                    NOTHING: null
                    OBJECT:
                      nested: true
                """.trimIndent(),
            )

        assertEquals("Operator", (values.getValue("NAME") as ClearTermsJsonNode.ScalarNode.StringNode).value)
        assertEquals(42.0, (values.getValue("COUNT") as ClearTermsJsonNode.ScalarNode.NumberNode).value)
        assertTrue(values.getValue("NOTHING") is ClearTermsJsonNode.ScalarNode.NullNode)
        assertTrue("OBJECT" !in values)
    }

    @Test
    fun `extract operator values best effort returns empty for invalid yaml and non object values`() {
        val factory = createLegalDocumentFactory()

        assertTrue(factory.extractOperatorValuesBestEffort("kind: profile\noverrides: [").isEmpty())
        assertTrue(
            factory
                .extractOperatorValuesBestEffort(
                    """
                    kind: profile
                    overrides:
                      values: "not-an-object"
                    """.trimIndent(),
                ).isEmpty(),
        )
    }
}
