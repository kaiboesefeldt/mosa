package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsRendererTest {
    private val hasher = FixedHashProvider(byteArrayOf(0x01, 0x02))
    private val encoder = FixedTextEncoder()
    private val localeProvider = TestClearTermsLocaleProvider()
    private val renderer = ClearTermsRenderer(ClearTermsJsonParser(), hasher, encoder, localeProvider = localeProvider)

    @Test
    fun `renders baseline default and deviation`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "retention", "text": "Retention: {{duration:retention_period??as long as necessary}}.\n" },
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": {
                    "retention_period": null,
                    "operator_name": "Example Organization"
                  }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            val expected =
                """
                Retention: as long as necessary.
                Operator: Example Organization.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `applies deviation value overrides`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "retention", "text": "Retention: {{duration:retention_period??as long as necessary}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "retention_period": "30d" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": { "retention_period": "infinite" } }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            val expected =
                """
                Retention: infinite.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `applies text overrides with alignment`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "support", "text": "Support: {{weekday:support_day??business days}}." }
                    ],
                    "de": [
                      { "id": "support", "text": "Support: {{weekday:support_day??Werktagen}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": {
                          "support": "Support: {{weekday:support_day}}.\nNote: responses vary.\n"
                        },
                        "append": [
                          { "id": "extra", "text": "Additional terms." }
                        ]
                      },
                      "de": {
                        "replace": {
                          "support": "Support: {{weekday:support_day}}.\nHinweis: Antworten variieren."
                        },
                        "append": [
                          { "id": "extra", "text": "Zusätzliche Bestimmungen." }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            val expected =
                """
                Support: Monday.
                Note: responses vary.
                Additional terms.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `allows fallback placeholders in deviation replacement text`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "max_records": 5 }""")
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "limit": "Limit: {{number:max_records??none}}." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Limit: 5.", result)
        }

    @Test
    fun `rejects missing placeholder value`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": {}
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `renders when default omits value and deviation provides it`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": {}
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": { "operator_name": "Example Organization" } }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Operator: Example Organization.", result)
        }

    @Test
    fun `rejects unused default values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "operator", "text": "Operator: {{string:operator_name}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "operator_name": "Example Org", "unused": "extra" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Unknown placeholder 'unused'", result.message)
        }

    @Test
    fun `rejects unused values`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "operator_name": "Example Organization", "extra_value": 12 }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects invalid reference hash`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:FFFF" },
                  "values": { "operator_name": "Example Organization" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects baseline block misalignment`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "one", "text": "One" } ],
                    "de": [ { "id": "two", "text": "Zwei" } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": {}
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects duplicate baseline block ids per language`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "one", "text": "One" }, { "id": "two", "text": "Two" } ],
                    "de": [ { "id": "dup", "text": "Eins" }, { "id": "dup", "text": "Zwei" } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": {}
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Baseline block ids must be unique for 'de'", result.message)
        }

    @Test
    fun `rejects deviation texts missing language`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ],
                    "de": [ { "id": "support", "text": "Support: {{weekday:support_day??Werktagen}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": { "replace": { "support": "Support: {{weekday:support_day}}." }, "append": [] }
                    }
                  }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects replace of missing block`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": { "en": { "replace": { "missing": "X" }, "append": [] } }
                  }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects append id collision`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "support": "Support: {{weekday:support_day}}." },
                        "append": [ { "id": "support", "text": "Extra." } ]
                      }
                    }
                  }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects append text with fallback placeholders`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "support": "Support: {{weekday:support_day}}." },
                        "append": [ { "id": "extra", "text": "Extra: {{string:note??fallback}}." } ]
                      }
                    }
                  }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects duplicate append block ids`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "support": "Support: {{weekday:support_day}}." },
                        "append": [
                          { "id": "extra", "text": "Extra one." },
                          { "id": "extra", "text": "Extra two." }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Append block ids must be unique", result.message)
        }

    @Test
    fun `rejects placeholder type mismatch`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "limit", "text": "Limit: {{number:max_records??a limited number}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "max_records": "not-a-number" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects null without fallback`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "retention", "text": "Retention: {{duration:retention_period}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "retention_period": null }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `renders escaped braces`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Literal \\{{ and \\}} then {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "operator_name": "Example" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            val expected = "Literal {{ and }} then Example."
            assertEquals(expected, result)
        }

    @Test
    fun `rejects invalid time value`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "start_time": "2025-01-02T10:00:00" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects time values with invalid month`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "start_time": "2025-13-02T10:00:00Z" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects time values with invalid day`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "start_time": "2025-01-00T10:00:00Z" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects time values with invalid hour`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "start_time": "2025-01-02T25:00:00Z" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects time values with invalid second`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "start_time": "2025-01-02T10:00:60Z" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects unavailable language`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val defaultProfile = defaultJson(valuesJson = """{ }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "fr")
            }
        }

    @Test
    fun `rejects baseline reference id mismatch`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""", id = "baseline-a")
            val defaultProfile =
                defaultJson(
                    baselineId = "baseline-b",
                    valuesJson = """{ }""",
                )
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects baseline reference version mismatch`() =
        runTest {
            val baseline =
                baselineJson(
                    """{ "en": [ { "id": "one", "text": "One" } ] }""",
                    version = "1.1",
                )
            val defaultProfile =
                defaultJson(
                    baselineVersion = "1.0",
                    valuesJson = """{ }""",
                )
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects default reference hash mismatch`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val defaultProfile = defaultJson(valuesJson = """{ }""")
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:FFFF" },
                  "overrides": { "values": { } }
                }
                """.trimIndent()

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects default reference id mismatch`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val defaultProfile = defaultJson(valuesJson = """{ }""", id = "default-a")
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    defaultId = "default-b",
                )

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Reference id mismatch", result.message)
        }

    @Test
    fun `rejects default reference version mismatch`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val defaultProfile = defaultJson(valuesJson = """{ }""", version = "1.0")
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    defaultVersion = "2.0",
                )

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Reference version mismatch", result.message)
        }

    @Test
    fun `rejects replace keys mismatch across languages`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}" } ],
                      "de": [ { "id": "support", "text": "Support: {{string:level}}" } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile =
                defaultJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "A" }, "append": [] },
                          "de": { "replace": { "other": "B" }, "append": [] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects append ids mismatch across languages`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}" } ],
                      "de": [ { "id": "support", "text": "Support: {{string:level}}" } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile =
                defaultJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "A" }, "append": [ { "id": "extra", "text": "E" } ] },
                          "de": { "replace": { "support": "B" }, "append": [ { "id": "extra2", "text": "E2" } ] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `keeps baseline text when no replacement provided`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [
                        { "id": "one", "text": "One." },
                        { "id": "two", "text": "Two." }
                      ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ }""")
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "one": "Uno." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Uno.Two.", result)
        }

    @Test
    fun `allows fallbacks in deviation text`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}" } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile =
                defaultJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "Support: {{string:level??fallback}}" }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Support: basic", result)
        }

    @Test
    fun `rejects placeholder type mismatch across blocks`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [
                        { "id": "one", "text": "One {{string:value}}" },
                        { "id": "two", "text": "Two {{number:value}}" }
                      ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "text" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `renders number fallback when value is null`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records??unknown}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "max_records": null }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Limit: unknown.", result)
        }

    @Test
    fun `renders string fallback when value is null`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "operator", "text": "Operator: {{string:operator_name??unspecified}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "operator_name": null }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Operator: unspecified.", result)
        }

    @Test
    fun `renders integer numbers without decimal`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "max_records": 10 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Limit: 10.", result)
        }

    @Test
    fun `renders decimal numbers with fraction`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "max_records": 10.5 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Limit: 10.5.", result)
        }

    @Test
    fun `renders negative decimal numbers`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "max_records": -10.25 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Limit: -10.25.", result)
        }

    @Test
    fun `rejects string placeholder with non string value`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "operator", "text": "Operator: {{string:name}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "name": 12 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects duration placeholder with non string value`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": 12 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects invalid duration values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": "1x" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects too short duration values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": "s" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects duration values with non numeric quantity`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": "as" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects duration values with unmatched quotes`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": "\"30d" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `renders quoted duration values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "retention", "text": "Retention: {{duration:period}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "period": "  \"30d\"  " }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Retention:   \"30d\"  .", result)
        }

    @Test
    fun `renders valid time value`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-01-02T10:00:00Z" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on January 2, 2025 at 10:00:00Z.", result)
        }

    @Test
    fun `renders time value without seconds`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-01-02T10:30Z" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on January 2, 2025 at 10:30Z.", result)
        }

    @Test
    fun `renders time value with negative offset`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-01-02T10:00-05:00" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on January 2, 2025 at 10:00-05:00.", result)
        }

    @Test
    fun `renders time fallback when value is null`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": null }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: soon.", result)
        }

    @Test
    fun `rejects time placeholder with non string value`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": 10 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `renders valid weekday values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "support_day": "mon" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Support: Monday.", result)
        }

    @Test
    fun `renders wildcard time with timezone`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-*-15T12:00:00+01:00" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on day 15 of every month at 12:00:00+01:00.", result)
        }

    @Test
    fun `renders month and year time pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-02-*" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: in February 2025.", result)
        }

    @Test
    fun `renders day of every month in year pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-*-05" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on day 5 of every month in 2025.", result)
        }

    @Test
    fun `renders year only time pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-*-*" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: in 2025.", result)
        }

    @Test
    fun `renders month day every year pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-02-05" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on February 5 every year.", result)
        }

    @Test
    fun `renders every day in month pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-02-*" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: every day in February.", result)
        }

    @Test
    fun `renders day of every month pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-*-15" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on day 15 of every month.", result)
        }

    @Test
    fun `renders every day pattern`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-*-*" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: every day.", result)
        }

    @Test
    fun `renders overflow day as last day`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-02-31" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of February every year.", result)
        }

    @Test
    fun `renders overflow day with year and month`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-02-31" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of February 2025.", result)
        }

    @Test
    fun `renders overflow day in all thirty day months`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val monthByNumber =
                mapOf(
                    4 to "April",
                    6 to "June",
                    9 to "September",
                    11 to "November",
                )

            monthByNumber.forEach { (month, monthName) ->
                val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-${month.toString().padStart(2, '0')}-31" }""")
                val deviation = deviationJson(valuesJson = """{ }""")

                val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

                assertEquals("Start: on the last day of $monthName 2025.", result)
            }
        }

    @Test
    fun `renders day 31 behavior for all months`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val expectedByMonth =
                mapOf(
                    1 to "Start: on January 31, 2025.",
                    2 to "Start: on the last day of February 2025.",
                    3 to "Start: on March 31, 2025.",
                    4 to "Start: on the last day of April 2025.",
                    5 to "Start: on May 31, 2025.",
                    6 to "Start: on the last day of June 2025.",
                    7 to "Start: on July 31, 2025.",
                    8 to "Start: on August 31, 2025.",
                    9 to "Start: on the last day of September 2025.",
                    10 to "Start: on October 31, 2025.",
                    11 to "Start: on the last day of November 2025.",
                    12 to "Start: on December 31, 2025.",
                )

            expectedByMonth.forEach { (month, expectedText) ->
                val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-${month.toString().padStart(2, '0')}-31" }""")
                val deviation = deviationJson(valuesJson = """{ }""")

                val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

                assertEquals(expectedText, result)
            }
        }

    @Test
    fun `renders leap day as full date`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2024-02-29" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on February 29, 2024.", result)
        }

    @Test
    fun `renders overflow day in non leap year`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2023-02-29" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of February 2023.", result)
        }

    @Test
    fun `renders overflow day in century non leap year`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "1900-02-29" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of February 1900.", result)
        }

    @Test
    fun `renders overflow day for every month in year`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "2025-*-31" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of every month in 2025.", result)
        }

    @Test
    fun `renders overflow day for every month`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "start", "text": "Start: {{time:start_time}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "start_time": "*-*-31" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Start: on the last day of every month.", result)
        }

    @Test
    fun `renders number with decimal formatting and grouping`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "price", "text": "Price: {{number:amount?format=decimal(2)&grouping=on}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "amount": 1234.5 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Price: 1,234.50.", result)
        }

    @Test
    fun `renders number with integer formatting without grouping`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=integer&grouping=off}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": 1234.6 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: 1235.", result)
        }

    @Test
    fun `renders negative integer rounding away from zero`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=integer&grouping=off}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": -1.5 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: -2.", result)
        }

    @Test
    fun `renders decimal format with zero scale`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=decimal(0)&grouping=on}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": 1200.4 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: 1,200.", result)
        }

    @Test
    fun `renders decimal format without grouping`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=decimal(2)&grouping=off}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": 1234.5 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: 1234.50.", result)
        }

    @Test
    fun `renders decimal format for scientific notation values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=decimal(2)&grouping=off}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": 1e7 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: 10000000.00.", result)
        }

    @Test
    fun `renders grouped integer without separator at three digits`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "count", "text": "Count: {{number:count?format=integer&grouping=on}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "count": 999.0 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Count: 999.", result)
        }

    @Test
    fun `renders number with default formatting`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "ratio", "text": "Ratio: {{number:ratio}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "ratio": 1.25 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Ratio: 1.25.", result)
        }

    @Test
    fun `renders weekday fallback when value is null`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day??weekdays}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "support_day": null }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Support: weekdays.", result)
        }

    @Test
    fun `renders unknown weekday without localization`() =
        runTest {
            val locale =
                ClearTermsLocale(
                    language = "en",
                    months =
                        listOf(
                            "January",
                            "February",
                            "March",
                            "April",
                            "May",
                            "June",
                            "July",
                            "August",
                            "September",
                            "October",
                            "November",
                            "December",
                        ),
                    weekdays =
                        mapOf(
                            "mon" to "Monday",
                            "tue" to "Tuesday",
                            "wed" to "Wednesday",
                            "thu" to "Thursday",
                            "fri" to "Friday",
                            "sat" to "Saturday",
                        ),
                    decimalSeparator = '.',
                    groupingSeparator = ',',
                    atWord = "at",
                    patterns =
                        ClearTermsLocale.Patterns(
                            onFullDate = "on {month} {day}, {year}",
                            inMonthYear = "in {month} {year}",
                            onDayOfEveryMonthInYear = "on day {day} of every month in {year}",
                            inYear = "in {year}",
                            onMonthDayEveryYear = "on {month} {day} every year",
                            everyDayInMonth = "every day in {month}",
                            onDayOfEveryMonth = "on day {day} of every month",
                            everyDay = "every day",
                            onLastDayOfMonth = "on the last day of {month} {year}",
                            onLastDayOfMonthEveryYear = "on the last day of {month} every year",
                            onLastDayOfEveryMonthInYear = "on the last day of every month in {year}",
                            onLastDayOfEveryMonth = "on the last day of every month",
                        ),
                )
            val renderer =
                ClearTermsRenderer(
                    ClearTermsJsonParser(),
                    hasher,
                    encoder,
                    localeProvider =
                        object : ClearTermsLocaleProvider {
                            override fun resolve(languageTag: String): ClearTermsLocale = locale
                        },
                )
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "support_day": "sun" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Support: sun.", result)
        }

    @Test
    fun `renders values using english fallback locale`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "fr": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "values": { "support_day": "mon" }
                }
                """.trimIndent()
            val deviation =
                """
                {
                  "kind": "deviation",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "fr")

            assertEquals("Support: Monday.", result)
        }

    @Test
    fun `rejects weekday placeholder with non string value`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "support_day": 2 }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects invalid weekday values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "support_day": "monday" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects missing values for placeholders introduced by deviations`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "level": "basic" }""")
            val deviation =
                deviationJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "Support: {{string:level}} {{string:detail}}." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unresolved placeholder syntax`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{string:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `allows rendered text with blank placeholder options`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{number:inner?}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Value: {{number:inner?}}.", result)
        }

    @Test
    fun `rejects rendered text with non blank placeholder options`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{number:inner?format}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unresolved number placeholder syntax`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{number:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unresolved duration placeholder syntax`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{duration:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unresolved time placeholder syntax`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{time:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unresolved weekday placeholder syntax`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{weekday:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `rejects rendered text with unterminated placeholder`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{string:inner" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
        }

    @Test
    fun `allows rendered text with unsupported placeholder types`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{bogus:inner}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Value: {{bogus:inner}}.", result)
        }

    @Test
    fun `allows rendered text with placeholder keys containing whitespace`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{string:bad key}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Value: {{string:bad key}}.", result)
        }

    @Test
    fun `allows rendered text with empty placeholder keys`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{string:}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Value: {{string:}}.", result)
        }

    @Test
    fun `allows rendered text with non placeholder braces`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "token", "text": "Value: {{string:value}}." } ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ "value": "{{not_placeholder}}" }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

            assertEquals("Value: {{not_placeholder}}.", result)
        }

    @Test
    fun `rejects duplicate baseline block ids`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [
                        { "id": "dup", "text": "One" },
                        { "id": "dup", "text": "Two" }
                      ]
                    }
                    """.trimIndent(),
                )
            val defaultProfile = defaultJson(valuesJson = """{ }""")
            val deviation = deviationJson(valuesJson = """{ }""")

            val result =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
                }

            assertEquals("Baseline block ids must be unique", result.message)
        }

    private fun baselineJson(
        textsJson: String,
        id: String = "example-policy",
        version: String = "1.0",
    ): String =
        """
        {
          "kind": "baseline",
          "id": "$id",
          "version": "$version",
          "texts": $textsJson
        }
        """.trimIndent()

    private fun defaultJson(
        valuesJson: String,
        baselineId: String = "example-policy",
        baselineVersion: String = "1.0",
        id: String = "example-policy-default",
        version: String = "1.0",
    ): String =
        """
        {
          "kind": "default",
          "id": "$id",
          "version": "$version",
          "baseline": { "id": "$baselineId", "version": "$baselineVersion", "hash": "sha256:0102" },
          "values": $valuesJson
        }
        """.trimIndent()

    private fun deviationJson(
        valuesJson: String,
        defaultId: String = "example-policy-default",
        defaultVersion: String = "1.0",
        textsJson: String? = null,
    ): String {
        val textsPart =
            if (textsJson == null) {
                ""
            } else {
                """
                ,
                  "texts": $textsJson
                """.trimIndent()
            }
        return """
            {
              "kind": "deviation",
              "id": "example-policy-operator",
              "version": "1.0",
              "default": { "id": "$defaultId", "version": "$defaultVersion", "hash": "sha256:0102" },
              "overrides": {
                "values": $valuesJson$textsPart
              }
            }
            """.trimIndent()
    }

    private class FixedHashProvider(
        private val bytes: ByteArray,
    ) : ClearTermsHashProvider {
        override suspend fun sha256(data: ByteArray): ByteArray = bytes
    }

    private class FixedTextEncoder : ClearTermsTextEncoder {
        override fun encode(value: String): ByteArray = value.encodeToByteArray()
    }

    private class TestClearTermsLocaleProvider : ClearTermsLocaleProvider {
        private val locales =
            mapOf(
                "en" to
                    ClearTermsLocale(
                        language = "en",
                        months =
                            listOf(
                                "January",
                                "February",
                                "March",
                                "April",
                                "May",
                                "June",
                                "July",
                                "August",
                                "September",
                                "October",
                                "November",
                                "December",
                            ),
                        weekdays =
                            mapOf(
                                "mon" to "Monday",
                                "tue" to "Tuesday",
                                "wed" to "Wednesday",
                                "thu" to "Thursday",
                                "fri" to "Friday",
                                "sat" to "Saturday",
                                "sun" to "Sunday",
                            ),
                        decimalSeparator = '.',
                        groupingSeparator = ',',
                        atWord = "at",
                        patterns =
                            ClearTermsLocale.Patterns(
                                onFullDate = "on {month} {day}, {year}",
                                inMonthYear = "in {month} {year}",
                                onDayOfEveryMonthInYear = "on day {day} of every month in {year}",
                                inYear = "in {year}",
                                onMonthDayEveryYear = "on {month} {day} every year",
                                everyDayInMonth = "every day in {month}",
                                onDayOfEveryMonth = "on day {day} of every month",
                                everyDay = "every day",
                                onLastDayOfMonth = "on the last day of {month} {year}",
                                onLastDayOfMonthEveryYear = "on the last day of {month} every year",
                                onLastDayOfEveryMonthInYear = "on the last day of every month in {year}",
                                onLastDayOfEveryMonth = "on the last day of every month",
                            ),
                    ),
                "de" to
                    ClearTermsLocale(
                        language = "de",
                        months =
                            listOf(
                                "Januar",
                                "Februar",
                                "Maerz",
                                "April",
                                "Mai",
                                "Juni",
                                "Juli",
                                "August",
                                "September",
                                "Oktober",
                                "November",
                                "Dezember",
                            ),
                        weekdays =
                            mapOf(
                                "mon" to "Montag",
                                "tue" to "Dienstag",
                                "wed" to "Mittwoch",
                                "thu" to "Donnerstag",
                                "fri" to "Freitag",
                                "sat" to "Samstag",
                                "sun" to "Sonntag",
                            ),
                        decimalSeparator = ',',
                        groupingSeparator = '.',
                        atWord = "um",
                        patterns =
                            ClearTermsLocale.Patterns(
                                onFullDate = "am {day}. {month} {year}",
                                inMonthYear = "im {month} {year}",
                                onDayOfEveryMonthInYear = "am {day}. jedes Monats im Jahr {year}",
                                inYear = "im Jahr {year}",
                                onMonthDayEveryYear = "am {day}. {month} jedes Jahr",
                                everyDayInMonth = "jeden Tag im {month}",
                                onDayOfEveryMonth = "am {day}. jedes Monats",
                                everyDay = "jeden Tag",
                                onLastDayOfMonth = "am letzten Tag des {month} {year}",
                                onLastDayOfMonthEveryYear = "am letzten Tag des {month} jedes Jahr",
                                onLastDayOfEveryMonthInYear = "am letzten Tag jedes Monats im Jahr {year}",
                                onLastDayOfEveryMonth = "am letzten Tag jedes Monats",
                            ),
                    ),
            )

        override fun resolve(languageTag: String): ClearTermsLocale {
            val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
            return locales[primary] ?: locales.getValue("en")
        }
    }
}
