package straightway.mosa.legal.clearterms

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClearTermsYamlLanguageFragmentMergerTest {
    private val merger = ClearTermsYamlLanguageFragmentMerger()
    private val yamlMapper = ObjectMapper(YAMLFactory())

    @Test
    fun `merges baseline texts from language fragments`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              de:
                - id: intro
                  text: "Hallo"
            """.trimIndent()
        val englishFragment =
            """
            texts:
              en:
                - id: intro
                  text: "Hello"
            """.trimIndent()

        val mergedYaml = merger.merge(baseYaml, listOf(englishFragment))
        val merged = yamlMapper.readTree(mergedYaml)

        assertEquals(setOf("de", "en"), merged["texts"].fieldNames().asSequence().toSet())
        assertEquals("Hallo", merged["texts"]["de"][0]["text"].asText())
        assertEquals("Hello", merged["texts"]["en"][0]["text"].asText())
    }

    @Test
    fun `merges profile overrides texts from language fragments`() {
        val baseYaml =
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
        val germanFragment =
            """
            overrides:
              texts:
                de:
                  replace:
                    intro: "Hallo"
            """.trimIndent()

        val mergedYaml = merger.merge(baseYaml, listOf(germanFragment))
        val merged = yamlMapper.readTree(mergedYaml)

        assertEquals("Alice", merged["overrides"]["values"]["name"].asText())
        assertEquals("Hallo", merged["overrides"]["texts"]["de"]["replace"]["intro"].asText())
    }

    @Test
    fun `rejects duplicate language definition across baseline and fragment`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts:
              de:
                - id: intro
                  text: "Hallo"
            """.trimIndent()
        val duplicateGermanFragment =
            """
            texts:
              de:
                - id: intro
                  text: "Servus"
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf(duplicateGermanFragment))
            }

        assertEquals("Language 'de' is defined more than once", exception.message)
    }

    @Test
    fun `returns base yaml unchanged when no fragments are provided`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts: {}
            """.trimIndent()

        val mergedYaml = merger.merge(baseYaml, emptyList())

        assertEquals(baseYaml, mergedYaml)
    }

    @Test
    fun `rejects unsupported document kind`() {
        val baseYaml =
            """
            kind: default
            id: default-1
            version: "1.0"
            values: {}
            """.trimIndent()
        val fragment =
            """
            texts: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf(fragment))
            }

        assertEquals("Language fragments are only supported for baseline and profile documents", exception.message)
    }

    @Test
    fun `rejects invalid yaml in fragment`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf("texts: ["))
            }

        assertTrue(checkNotNull(exception.message).contains("Fragment #1 is not valid YAML"))
    }

    @Test
    fun `rejects non mapping root in fragment`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf("- item"))
            }

        assertEquals("Fragment #1 root must be a mapping", exception.message)
    }

    @Test
    fun `rejects baseline fragment with unexpected keys`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts: {}
            """.trimIndent()
        val fragment =
            """
            foo: bar
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf(fragment))
            }

        assertEquals("Baseline fragment #1 must contain only: texts", exception.message)
    }

    @Test
    fun `rejects profile fragment with invalid structure`() {
        val baseYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            overrides: {}
            """.trimIndent()
        val fragment =
            """
            overrides:
              values: {}
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf(fragment))
            }

        assertEquals("Profile fragment #1 overrides must contain only: texts", exception.message)
    }

    @Test
    fun `creates missing profile overrides and texts maps before merging`() {
        val baseYaml =
            """
            kind: profile
            id: profile-1
            version: "1.0"
            parent:
              id: baseline-1
              version: "1.0"
            """.trimIndent()
        val fragment =
            """
            overrides:
              texts:
                en:
                  replace:
                    intro: "Hello"
            """.trimIndent()

        val mergedYaml = merger.merge(baseYaml, listOf(fragment))
        val merged = yamlMapper.readTree(mergedYaml)

        assertEquals("Hello", merged["overrides"]["texts"]["en"]["replace"]["intro"].asText())
    }

    @Test
    fun `rejects non mapping texts in baseline fragment`() {
        val baseYaml =
            """
            kind: baseline
            id: baseline-1
            version: "1.0"
            texts: {}
            """.trimIndent()
        val fragment =
            """
            texts: "not-a-map"
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                merger.merge(baseYaml, listOf(fragment))
            }

        assertEquals("Baseline fragment #1 key 'texts' must be a mapping", exception.message)
    }
}
