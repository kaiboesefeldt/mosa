package straightway.mosa.legal.clearterms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsDocumentReaderTest {
    private val parser = ClearTermsJsonParser()
    private val reader = ClearTermsDocumentReader()

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

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `rejects baseline with invalid kind`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy",
              "version": "1.0",
              "texts": { "en": [ { "id": "one", "text": "One" } ] }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
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

        assertFailsWith<ClearTermsValidationException> {
            reader.readBaseline(parser.parse(json))
        }
    }

    @Test
    fun `reads default profile`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "values": {
                "operator_name": "Example",
                "max_records": 10,
                "retention_period": null
              }
            }
            """.trimIndent()

        val profile = reader.readDefault(parser.parse(json))

        assertEquals("example-policy-default", profile.id)
        assertEquals("example-policy", profile.baseline.id)
        assertEquals("sha256:abc", profile.baseline.hash)
        assertEquals(3, profile.values.size)
    }

    @Test
    fun `rejects default with invalid kind`() {
        val json =
            """
            {
              "kind": "baseline",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "values": {}
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDefault(parser.parse(json))
        }
    }

    @Test
    fun `rejects default with non string baseline hash`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": { "id": "example-policy", "version": "1.0", "hash": 10 },
              "values": {}
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDefault(parser.parse(json))
        }
    }

    @Test
    fun `rejects default with baseline not a mapping`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": "nope",
              "values": {}
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDefault(parser.parse(json))
        }
    }

    @Test
    fun `rejects default with missing baseline hash`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": { "id": "example-policy", "version": "1.0" },
              "values": {}
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDefault(parser.parse(json))
        }
    }

    @Test
    fun `rejects default with non scalar values`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-default",
              "version": "1.0",
              "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:abc" },
              "values": {
                "operator_name": { "nested": "nope" }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDefault(parser.parse(json))
        }
    }

    @Test
    fun `reads deviation with text overrides`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": { "max_records": null },
                "texts": {
                  "en": {
                    "replace": { "support": "Support text" },
                    "append": [ { "id": "extra", "text": "Extra" } ]
                  },
                  "de": {
                    "replace": { "support": "Support DE" },
                    "append": [ { "id": "extra", "text": "Extra DE" } ]
                  }
                }
              }
            }
            """.trimIndent()

        val deviation = reader.readDeviation(parser.parse(json))

        assertEquals("example-policy-operator", deviation.id)
        assertEquals(1, deviation.overrides.values.size)
        assertEquals(setOf("en", "de"), deviation.overrides.texts?.keys)
        val enOverrides = deviation.overrides.texts?.getValue("en")
        assertEquals("Support text", enOverrides?.replace?.get("support"))
        assertEquals("extra", enOverrides?.append?.first()?.id)
    }

    @Test
    fun `rejects deviation with invalid kind`() {
        val json =
            """
            {
              "kind": "default",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": { "values": {} }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `reads deviation without texts`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": { "values": {} }
            }
            """.trimIndent()

        val deviation = reader.readDeviation(parser.parse(json))

        assertEquals(emptyMap(), deviation.overrides.values)
        assertEquals(null, deviation.overrides.texts)
    }

    @Test
    fun `rejects deviation missing overrides values`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "texts": { "en": { "replace": {}, "append": [] } }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation overrides with non scalar values`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": { "max_records": { "nested": 1 } }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                reader.readDeviation(parser.parse(json))
            }

        assertEquals("Override values must be scalar", exception.message)
    }

    @Test
    fun `rejects deviation overrides with unknown key`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": { "values": {}, "extra": true }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation texts that are not a mapping`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": { "values": {}, "texts": [] }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation language override that is not a mapping`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": { "en": "invalid" }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation text override with missing replace`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": { "en": { "append": [] } }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation text override with missing append`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": { "en": { "replace": { "support": "Support" } } }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation text override with non list append`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": { "en": { "replace": {}, "append": "invalid" } }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                reader.readDeviation(parser.parse(json))
            }

        assertEquals("Key 'append' must be a list", exception.message)
    }

    @Test
    fun `rejects deviation text override with non string replace`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": { "support": { "nested": true } },
                    "append": []
                  }
                }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation text override with numeric replace text`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": { "support": 12 },
                    "append": []
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                reader.readDeviation(parser.parse(json))
            }

        assertEquals("Replace text must be string", exception.message)
    }

    @Test
    fun `rejects deviation text override with unknown key`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": {},
                    "append": [],
                    "extra": "invalid"
                  }
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                reader.readDeviation(parser.parse(json))
            }

        assertEquals("Unknown key 'extra'", exception.message)
    }

    @Test
    fun `rejects deviation append block missing text`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
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

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }

    @Test
    fun `rejects deviation append block that is not a mapping`() {
        val json =
            """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:def" },
              "overrides": {
                "values": {},
                "texts": {
                  "en": {
                    "replace": {},
                    "append": [ "invalid" ]
                  }
                }
              }
            }
            """.trimIndent()

        assertFailsWith<ClearTermsValidationException> {
            reader.readDeviation(parser.parse(json))
        }
    }
}
