@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.parse.JsonNode
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsDocumentReaderTest {
    private val parser = JsonParser()
    private val reader = DocumentReader()

    @Test
    fun `reads baseline with multiple languages`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  { "id": "one", "text": "One" },
                  { "id": "two", "text": "Two" }
                ],
                "de": [
                  { "id": "one", "text": "Eins" },
                  { "id": "two", "text": "Zwei" }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))

        assertEquals("example-policy", baseline.id)
        assertEquals("1.0", baseline.version)
        assertEquals(setOf("en", "de"), baseline.textsByLanguage.keys)
        assertEquals(listOf("one", "two"), baseline.textsByLanguage.getValue("en").map { it.id })
    }

    @Test
    fun `rejects baseline without texts`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {}
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline with unknown key`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": "one", "text": "One" } ] },
              "extra": true
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline block missing id`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "text": "One" } ] }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline with invalid kind`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": "one", "text": "One" } ] }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline texts that are not arrays`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": { "id": "one", "text": "One" } }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline block that is not a mapping`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ "text" ] }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline block with unknown key`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": "one", "text": "One", "extra": true } ] }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline block with non string id`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": 3, "text": "One" } ] }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline block with non string text`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": "one", "text": 3 } ] }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Key 'text' must be a string", exception.message)
    }

    @Test
    fun `reads baseline block with null text`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [ { "id": "contact", "text": null } ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))

        assertEquals(
            null,
            baseline.textsByLanguage
                .getValue("en")
                .single()
                .text,
        )
    }

    @Test
    fun `reads profile with parent and overrides`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": { "operator_name": "Example Organization" },
                "texts": {
                  "en": {
                    "replace": { "contact": "Contact: support@example.org." },
                    "append": [ { "id": "extra", "text": "Extra." } ]
                  }
                }
              }
            }
            """.trimIndent()

        val profile = reader.readProfile(parser.parse(json))

        assertEquals("example-policy-a", profile.id)
        assertEquals("example-policy", profile.parent.id)
        assertEquals(
            "Example Organization",
            (profile.overrides.values?.getValue("operator_name") as JsonNode.ScalarNode.StringNode).value,
        )
        assertEquals(
            "Contact: support@example.org.",
            profile.overrides.texts
                ?.getValue("en")
                ?.replace
                ?.getValue("contact"),
        )
    }

    @Test
    fun `rejects profile append block with null text`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": {},
                    "append": [ { "id": "extra", "text": null } ]
                  }
                }
              }
            }
            """.trimIndent()

        assertFailsWith<ValidationException> {
            reader.readProfile(parser.parse(json))
        }
    }

    @Test
    fun `rejects profile append block missing text`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": {},
                    "append": [ { "id": "extra" } ]
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Missing key 'text'", exception.message)
    }

    @Test
    fun `rejects profile texts that are not a mapping`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": {},
                "texts": []
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Overrides texts must be a mapping", exception.message)
    }

    @Test
    fun `reads profile with missing overrides values`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "texts": {
                  "en": {
                    "replace": { "contact": "Contact: support@example.org." }
                  }
                }
              }
            }
            """.trimIndent()

        val profile = reader.readProfile(parser.parse(json))

        assertEquals(null, profile.overrides.values)
        assertEquals(
            "Contact: support@example.org.",
            profile.overrides.texts
                ?.getValue("en")
                ?.replace
                ?.get("contact"),
        )
    }

    @Test
    fun `rejects profile overrides values that are not a mapping`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": "invalid"
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Key 'values' must be a mapping", exception.message)
    }

    @Test
    fun `rejects profile language override that is not a mapping`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": "invalid"
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Overrides for 'en' must be a mapping", exception.message)
    }

    @Test
    fun `rejects profile text override with non string replace`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": { "contact": 12 },
                    "append": []
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Replace text for block 'contact' in language 'en' must be string", exception.message)
    }

    @Test
    fun `reads baseline block with hints`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": {
                      "expectedContent": "Provide support channels and response times.",
                      "override": "once",
                      "inlineWithNext": true
                    }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals("Provide support channels and response times.", block.hints?.expectedContent)
        assertEquals(TextOverrideSeverity.ONCE, block.hints?.override)
        assertEquals(true, block.hints?.inlineWithNext)
    }

    @Test
    fun `reads baseline hints with normal severity`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": "Contact.",
                    "hints": { "override": "ok" }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals(TextOverrideSeverity.OK, block.hints?.override)
    }

    @Test
    fun `reads baseline hints with alert severity`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": "Contact.",
                    "hints": { "override": "alert" }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals(TextOverrideSeverity.ALERT, block.hints?.override)
    }

    @Test
    fun `reads baseline hints with warn severity`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": "Contact.",
                    "hints": { "override": "warn" }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals(TextOverrideSeverity.WARN, block.hints?.override)
    }

    @Test
    fun `reads baseline hints with expected content and missing severity`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": { "expectedContent": "Provide contact details." }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals("Provide contact details.", block.hints?.expectedContent)
        assertEquals(null, block.hints?.override)
    }

    @Test
    fun `reads baseline hints with inlineWithNext false`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": "Contact.",
                    "hints": { "inlineWithNext": false }
                  }
                ]
              }
            }
            """.trimIndent()

        val baseline = reader.readBaseline(parser.parse(json))
        val block = baseline.textsByLanguage.getValue("en").single()

        assertEquals(false, block.hints?.inlineWithNext)
    }

    @Test
    fun `rejects profile replace mapping with hints`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "texts": {
                  "en": {
                    "replace": {
                      "contact": {
                        "text": "Contact: support@example.org.",
                        "hints": { "override": "once" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Replace text for block 'contact' in language 'en' must be string", exception.message)
    }

    @Test
    fun `rejects unknown hint override severity`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": { "override": "soft" }
                  }
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Unknown override severity 'soft'", exception.message)
    }

    @Test
    fun `rejects hints that are not a mapping`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": "invalid"
                  }
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Key 'hints' must be a mapping", exception.message)
    }

    @Test
    fun `rejects non string expectedContent in hints`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": { "expectedContent": 12 }
                  }
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Key 'expectedContent' must be a string", exception.message)
    }

    @Test
    fun `rejects non string override in hints`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": { "override": 5 }
                  }
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Key 'override' must be a string", exception.message)
    }

    @Test
    fun `rejects non boolean inlineWithNext in hints`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy",
              "version": "1.0",
              "texts": {
                "en": [
                  {
                    "id": "contact",
                    "text": null,
                    "hints": { "inlineWithNext": "yes" }
                  }
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readBaseline(parser.parse(json))
            }

        assertEquals("Key 'inlineWithNext' must be a boolean", exception.message)
    }

    @Test
    fun `rejects hints in profile append blocks`() {
        val json =
            """
            {
              "kind": "profile",
              "id": "example-policy-a",
              "version": "1.0",
              "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "overrides": {
                "texts": {
                  "en": {
                    "replace": { "contact": "Contact: support@example.org." },
                    "append": [
                      {
                        "id": "extra",
                        "text": "Extra.",
                        "hints": { "override": "once" }
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ValidationException> {
                reader.readProfile(parser.parse(json))
            }

        assertEquals("Unknown key 'hints'", exception.message)
    }
}
