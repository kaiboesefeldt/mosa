package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsYamlCanonicalJsonConverterTest {
    private val converter = ClearTermsYamlCanonicalJsonConverter()

    @Test
    fun `converts baseline and profile chain to canonical clear terms json artifacts`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello {{string:name}}"
            """.trimIndent()
        val profileOneYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values:
                name: "Alice"
            """.trimIndent()
        val profileTwoYaml =
            """
            kind: profile
            id: profile-2
            version: "1.0"
            parent:
              id: profile-1
              version: "1.0"
            overrides:
              values:
                name: "Bob"
              texts:
                en:
                  replace:
                    intro: "Hi {{string:name}}"
            """.trimIndent()

        val result = converter.convert(baselineYaml, listOf(profileOneYaml, profileTwoYaml))

        assertEquals("sha256:${sha256(result.baseline.canonicalJson)}", result.baseline.hash)
        assertEquals(
            result.baseline.hash,
            result.profiles[0]
                .json.entries
                .parentHash(),
        )
        assertEquals(
            result.profiles[0].hash,
            result.profiles[1]
                .json.entries
                .parentHash(),
        )
        assertEquals("sha256:${sha256(result.profiles[0].canonicalJson)}", result.profiles[0].hash)
        assertEquals("sha256:${sha256(result.profiles[1].canonicalJson)}", result.profiles[1].hash)
        assertTrue(result.baseline.canonicalJson.startsWith("{\"id\":\"baseline-1\""))
        assertTrue(result.profiles[0].canonicalJson.contains("\"hash\":\"${result.baseline.hash}\""))
        assertTrue(result.profiles[1].canonicalJson.contains("\"hash\":\"${result.profiles[0].hash}\""))
    }

    @Test
    fun `matches expected canonical json strings for baseline and profile`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello {{string:name}}"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values:
                name: "Alice"
            """.trimIndent()

        val result = converter.convert(baselineYaml, listOf(profileYaml))

        val expectedBaselineCanonical =
            "{\"id\":\"baseline-1\",\"kind\":\"baseline\",\"texts\":{\"en\":[{\"id\":\"intro\",\"text\":\"Hello {{string:name}}\"}]},\"version\":\"1.0\"}"
        val expectedBaselineHash = "sha256:${sha256(expectedBaselineCanonical)}"
        val expectedProfileCanonical =
            "{\"id\":\"profile-1\",\"kind\":\"profile\",\"overrides\":{\"values\":{\"name\":\"Alice\"}},\"parent\":{\"hash\":\"$expectedBaselineHash\",\"id\":\"baseline-1\",\"version\":\"1.0\"},\"version\":\"1.0\"}"

        assertEquals(expectedBaselineCanonical, result.baseline.canonicalJson)
        assertEquals(expectedProfileCanonical, result.profiles[0].canonicalJson)
    }

    @Test
    fun `provides canonical json chain as simple list`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello {{string:name}}"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values:
                name: "Alice"
            """.trimIndent()

        val result = converter.convert(baselineYaml, listOf(profileYaml))

        val expectedBaselineCanonical =
            "{\"id\":\"baseline-1\",\"kind\":\"baseline\",\"texts\":{\"en\":[{\"id\":\"intro\",\"text\":\"Hello {{string:name}}\"}]},\"version\":\"1.0\"}"
        val expectedProfileCanonical =
            "{\"id\":\"profile-1\",\"kind\":\"profile\",\"overrides\":{\"values\":{\"name\":\"Alice\"}},\"parent\":{\"hash\":\"${result.baseline.hash}\",\"id\":\"baseline-1\",\"version\":\"1.0\"},\"version\":\"1.0\"}"

        assertEquals(listOf(expectedBaselineCanonical, expectedProfileCanonical), result.canonicalJsonChain())
    }

    @Test
    fun `rejects empty profile chain`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, emptyList())
            }

        assertEquals("At least one profile is required", exception.message)
    }

    @Test
    fun `rejects first profile that does not reference baseline id and version`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: wrong-baseline
              version: "1.0"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Profile parent must reference previous artifact id/version", exception.message)
    }

    @Test
    fun `rejects first profile that does not reference baseline version`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "2.0"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Profile parent must reference previous artifact id/version", exception.message)
    }

    @Test
    fun `rejects profile parent hash inside yaml`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
              hash: "sha256:abc"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Unknown key 'hash'", exception.message)
    }

    @Test
    fun `rejects profile with unknown top level key`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values: {}
            extra: "present"
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Unknown key 'extra'", exception.message)
    }

    @Test
    fun `rejects profile with missing required key`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Missing key 'overrides'", exception.message)
    }

    @Test
    fun `rejects profile with non mapping parent`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent: baseline-1
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Key 'parent' must be a mapping", exception.message)
    }

    @Test
    fun `rejects profile with non string parent id`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: 123
              version: "1.0"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Key 'id' must be a string", exception.message)
    }

    @Test
    fun `rejects unsupported boolean value in overrides values`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello {{string:name}}"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values:
                name: true
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Override values must be scalar", exception.message)
    }

    @Test
    fun `supports number and null values in profile overrides`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: count
                  text: "{{number:count??0}}"
                - id: label
                  text: "{{string:label??unknown}}"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values:
                count: 42
                label: null
            """.trimIndent()

        val result = converter.convert(baselineYaml, listOf(profileYaml))

        assertTrue(result.profiles[0].canonicalJson.contains("\"count\":42"))
        assertTrue(result.profiles[0].canonicalJson.contains("\"label\":null"))
    }

    @Test
    fun `rejects invalid yaml document`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val invalidProfileYaml = "kind: profile\nid: profile-1\n:"

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(invalidProfileYaml))
            }

        assertTrue((exception.message ?: "").startsWith("Invalid YAML document:"))
    }

    @Test
    fun `rejects baseline yaml root that is not a mapping`() {
        val baselineYaml = "- kind: baseline"
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Root document must be a mapping", exception.message)
    }

    @Test
    fun `rejects yaml document with duplicate keys`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            id: baseline-2
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertTrue((exception.message ?: "").startsWith("Invalid YAML document:"))
    }

    @Test
    fun `rejects yaml root that is not a mapping`() {
        val baselineYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()
        val profileYaml = "- kind: profile"

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                converter.convert(baselineYaml, listOf(profileYaml))
            }

        assertEquals("Root document must be a mapping", exception.message)
    }

    private fun Map<String, ClearTermsJsonNode>.parentHash(): String {
        val parent = getValue("parent") as ClearTermsJsonNode.ObjectNode
        val hashNode = parent.entries.getValue("hash") as ClearTermsJsonNode.ScalarNode.StringNode
        return hashNode.value
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
