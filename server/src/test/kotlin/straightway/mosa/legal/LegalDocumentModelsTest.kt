package straightway.mosa.legal

import straightway.mosa.legal.clearterms.ClearTermsJsonNode
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegalDocumentModelsTest {
    @Test
    fun `legal documents return requested profile and expose chain response`() {
        val documents =
            LegalDocuments(
                baselineCanonicalJson = """{"id":"baseline"}""",
                profileCanonicalJsons = listOf("""{"id":"default"}""", """{"id":"operator"}"""),
                profileCanonicalJsonById =
                    mapOf(
                        "default" to """{"id":"default"}""",
                        "operator" to """{"id":"operator"}""",
                    ),
            )

        val response = documents.toDocumentChainResponse()

        assertEquals("""{"id":"operator"}""", documents.lastProfileCanonicalJson)
        assertEquals("""{"id":"default"}""", documents.requiredProfileCanonicalJson("default"))
        assertEquals("baseline", response.baseline["id"]?.toString()?.trim('"'))
        assertEquals(2, response.profiles.size)
    }

    @Test
    fun `legal documents fail when requested profile is missing`() {
        val documents =
            LegalDocuments(
                baselineCanonicalJson = """{"id":"baseline"}""",
                profileCanonicalJsons = listOf("""{"id":"operator"}"""),
                profileCanonicalJsonById = mapOf("operator" to """{"id":"operator"}"""),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                documents.requiredProfileCanonicalJson("missing")
            }

        assertEquals("Missing required legal profile 'missing'", exception.message)
    }

    @Test
    fun `mandatory chain context renders sorted version text`() {
        val context =
            MandatoryChainContext(
                baselineYaml = "kind: baseline",
                profileYamls = emptyList(),
                conversion = dummyConversionResult(),
                profileCanonicalJsons = emptyList(),
                lastProfile =
                    ProfileMetadata(
                        id = "service",
                        version = "1.0.0",
                        parent = ArtifactReference("default", "1.0.0"),
                    ),
                availableProfileVersions =
                    mapOf(
                        "service" to listOf("1.0.1", "1.0.0"),
                        "default" to listOf("1.0.0"),
                    ),
            )

        assertEquals("default=[1.0.0]; service=[1.0.1, 1.0.0]", context.availableProfileVersionsText())
    }

    @Test
    fun `operator profile metadata missing uses unknown placeholders`() {
        val metadata = OperatorProfileMetadata.missing("/tmp/missing.yaml")

        assertEquals("/tmp/missing.yaml", metadata.id)
        assertEquals("unknown", metadata.version)
        assertEquals("unknown", metadata.referencedParentId)
        assertEquals("unknown", metadata.referencedParentVersion)
    }

    @Test
    fun `canonical artifact profile id extracts id field`() {
        val artifact =
            ClearTermsYamlCanonicalJsonConverter.CanonicalArtifact(
                canonicalJson = """{"id":"profile-id"}""",
                hash = "hash",
                json =
                    ClearTermsJsonNode.ObjectNode(
                        mapOf(
                            "id" to ClearTermsJsonNode.ScalarNode.StringNode("profile-id"),
                        ),
                    ),
            )

        assertEquals("profile-id", artifact.profileId())
    }

    private fun dummyConversionResult() =
        ClearTermsYamlCanonicalJsonConverter.ConversionResult(
            baseline =
                ClearTermsYamlCanonicalJsonConverter.CanonicalArtifact(
                    canonicalJson = """{"id":"baseline"}""",
                    hash = "baseline-hash",
                    json =
                        ClearTermsJsonNode.ObjectNode(
                            mapOf(
                                "id" to ClearTermsJsonNode.ScalarNode.StringNode("baseline"),
                            ),
                        ),
                ),
            profiles = emptyList(),
        )
}
