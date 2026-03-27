@file:Suppress("ktlint:standard:no-wildcard-imports")

package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import straightway.mosa.legal.clearterms.document.*
import straightway.mosa.legal.clearterms.locale.*
import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.render.*
import straightway.mosa.legal.clearterms.spi.*
import straightway.mosa.legal.clearterms.validation.*
import straightway.mosa.legal.clearterms.value.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsRendererTest {
    private val hasher = FixedHashProvider(byteArrayOf(0x01, 0x02))
    private val encoder = FixedTextEncoder()
    private val localeProvider = TestClearTermsLocaleProvider()
    private val renderer =
        ClearTermsRenderer(
            hashProvider = hasher,
            textEncoder = encoder,
            localeProvider = localeProvider,
            parser = JsonParser(),
        )

    @Test
    fun `uses default json parser when none is provided`() =
        runTest {
            val renderer =
                ClearTermsRenderer(
                    hashProvider = hasher,
                    textEncoder = encoder,
                    localeProvider = localeProvider,
                )
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
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "operator_name": "Example Organization" }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(profile), "en").text

            assertEquals("Operator: Example Organization.", result)
        }

    @Test
    fun `renders baseline with two profile steps`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {
                    "retention_period": null,
                    "operator_name": "Example Organization"
                  }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, profileB), "en").text

            val expected =
                """
                Retention: as long as necessary.
                Operator: Example Organization.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `applies later profile value overrides`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "retention_period": "30d" }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": { "retention_period": "infinite" } }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, profileB), "en").text

            val expected =
                """
                Retention: indefinitely.
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
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

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

            val expected =
                """
                Support: Monday.
                Note: responses vary.
                Additional terms.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `allows fallback placeholders in later profile replacement text`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "limit", "text": "Limit: {{number:max_records}}." } ]
                    }
                    """.trimIndent(),
                )
            val firstProfile = firstProfileJson(valuesJson = """{ "max_records": 5 }""")
            val profileB =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "limit": "Limit: {{number:max_records??none}}." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, profileB), "en").text

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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            }
        }

    @Test
    fun `renders when first profile omits value and later profile provides it`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": { "operator_name": "Example Organization" } }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, profileB), "en").text

            assertEquals("Operator: Example Organization.", result)
        }

    @Test
    fun `ignores unused first profile values`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "operator", "text": "Operator: {{string:operator_name}}." } ]
                    }
                    """.trimIndent(),
                )
            val firstProfile = firstProfileJson(valuesJson = """{ "operator_name": "Example Org", "unused": "extra" }""")
            val profileB = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, profileB), "en").text

            assertEquals("Operator: Example Org.", result)
        }

    @Test
    fun `ignores unused values`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "operator_name": "Example Organization", "extra_value": 12 }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

            assertEquals("Operator: Example Organization.", result)
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:FFFF" },
                  "overrides": {
                    "values": { "operator_name": "Example Organization" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
                }

            assertEquals("Baseline block ids must be unique for 'de'", result.message)
        }

    @Test
    fun `supports later profile texts for subset of languages`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": { "replace": { "support": "Support: {{weekday:support_day}}." }, "append": [] }
                    }
                  }
                }
                """.trimIndent()

            val renderedEn = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            val exception =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "de").text
                }

            assertEquals("Support: Monday.", renderedEn)
            assertEquals("Language 'de' not available", exception.message)
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": {},
                    "texts": { "en": { "replace": { "missing": "X" }, "append": [] } }
                  }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
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

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
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

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
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
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "max_records": "not-a-number" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "retention_period": null }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "operator_name": "Example" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "start_time": "2025-01-02T10:00:00" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "start_time": "2025-13-02T10:00:00Z" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "start_time": "2025-01-00T10:00:00Z" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "start_time": "2025-01-02T25:00:00Z" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "start_time": "2025-01-02T10:00:60Z" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            }
        }

    @Test
    fun `rejects unavailable language`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val firstProfile = firstProfileJson(valuesJson = """{ }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "fr").text
            }
        }

    @Test
    fun `rejects baseline reference id mismatch`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""", id = "baseline-a")
            val firstProfile =
                firstProfileJson(
                    baselineId = "baseline-b",
                    valuesJson = """{ }""",
                )
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                firstProfileJson(
                    baselineVersion = "1.0",
                    valuesJson = """{ }""",
                )
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            }
        }

    @Test
    fun `rejects parent reference hash mismatch for later profile`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val firstProfile = firstProfileJson(valuesJson = """{ }""")
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:FFFF" },
                  "overrides": { "values": { } }
                }
                """.trimIndent()

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            }
        }

    @Test
    fun `rejects parent reference id mismatch for later profile`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val firstProfile = firstProfileJson(valuesJson = """{ }""", id = "default-a")
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    parentProfileId = "default-b",
                )

            val result =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
                }

            assertEquals("Reference id mismatch", result.message)
        }

    @Test
    fun `rejects parent reference version mismatch for later profile`() =
        runTest {
            val baseline = baselineJson("""{ "en": [ { "id": "one", "text": "One" } ] }""")
            val firstProfile = firstProfileJson(valuesJson = """{ }""", version = "1.0")
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    parentProfileVersion = "2.0",
                )

            val result =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                firstProfileJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "A" }, "append": [] },
                          "de": { "replace": { "other": "B" }, "append": [] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile =
                firstProfileJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "A" }, "append": [ { "id": "extra", "text": "E" } ] },
                          "de": { "replace": { "support": "B" }, "append": [ { "id": "extra2", "text": "E2" } ] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ }""")
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "one": "Uno." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

            assertEquals("Uno.Two.", result)
        }

    @Test
    fun `allows fallbacks in secondProfile text`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}" } ]
                    }
                    """.trimIndent(),
                )
            val firstProfile =
                firstProfileJson(
                    valuesJson = """{ "level": "basic" }""",
                )
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "Support: {{string:level??fallback}}" }, "append": [] }
                        }
                        """.trimIndent(),
                )

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "text" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "max_records": null }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "operator_name": null }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "max_records": 10 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "max_records": 10.5 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "max_records": -10.25 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "name": 12 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": 12 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": "1x" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": "s" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": "as" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": "\"30d" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "period": "  \"30d\"  " }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

            assertEquals("Retention: 30 days.", result)
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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-01-02T10:00:00Z" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-01-02T10:30Z" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-01-02T10:00-05:00" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": null }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": 10 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "support_day": "mon" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-*-15T12:00:00+01:00" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-02-*" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-*-05" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-*-*" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-02-05" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-02-*" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-*-15" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-*-*" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-02-31" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-02-31" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
                val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-${month.toString().padStart(2, '0')}-31" }""")
                val secondProfile = secondProfileJson(valuesJson = """{ }""")

                val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
                val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-${month.toString().padStart(2, '0')}-31" }""")
                val secondProfile = secondProfileJson(valuesJson = """{ }""")

                val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2024-02-29" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2023-02-29" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "1900-02-29" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "2025-*-31" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "start_time": "*-*-31" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "amount": 1234.5 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": 1234.6 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": -1.5 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": 1200.4 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": 1234.5 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": 1e7 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "count": 999.0 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "ratio": 1.25 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "support_day": null }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

            assertEquals("Support: weekdays.", result)
        }

    @Test
    fun `renders unknown weekday without localization`() =
        runTest {
            val locale =
                Locale(
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
                    decimalSeparator = ".",
                    groupingSeparator = ",",
                    atWord = "at",
                    patterns =
                        Locale.Patterns(
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
                    hashProvider = hasher,
                    textEncoder = encoder,
                    localeProvider =
                        object : ClearTermsLocaleProvider {
                            override fun resolve(languageTag: String): Locale = locale
                        },
                    parser = JsonParser(),
                )
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [ { "id": "support", "text": "Support: {{weekday:support_day}}." } ]
                    }
                    """.trimIndent(),
                )
            val firstProfile = firstProfileJson(valuesJson = """{ "support_day": "sun" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-default",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": {
                    "values": { "support_day": "mon" }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-operator",
                  "version": "1.0",
                  "parent": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
                  "overrides": { "values": {} }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "fr").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "support_day": 2 }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "support_day": "monday" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
            }
        }

    @Test
    fun `rejects missing values for placeholders introduced by later profiles`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    { 
                      "en": [ { "id": "support", "text": "Support: {{string:level}}." } ]
                    }
                    """.trimIndent(),
                )
            val firstProfile = firstProfileJson(valuesJson = """{ "level": "basic" }""")
            val secondProfile =
                secondProfileJson(
                    valuesJson = """{ }""",
                    textsJson =
                        """
                        {
                          "en": { "replace": { "support": "Support: {{string:level}} {{string:detail}}." }, "append": [] }
                        }
                        """.trimIndent(),
                )

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{string:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{number:inner?}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{number:inner?format}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{number:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{duration:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{time:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{weekday:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{string:inner" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            assertFailsWith<ValidationException> {
                renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{bogus:inner}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{string:bad key}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{string:}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ "value": "{{not_placeholder}}" }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text

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
            val firstProfile = firstProfileJson(valuesJson = """{ }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en").text
                }

            assertEquals("Baseline block ids must be unique", result.message)
        }

    @Test
    fun `carries inlineWithNext hint into rendered blocks`() =
        runTest {
            val baseline =
                baselineJson(
                    """
                    {
                      "en": [
                        {
                          "id": "rights-label",
                          "text": "- Access: ",
                          "hints": { "inlineWithNext": true }
                        },
                        { "id": "rights-action", "text": "Download data" }
                      ]
                    }
                    """.trimIndent(),
                )
            val firstProfile = firstProfileJson(valuesJson = """{ }""")
            val secondProfile = secondProfileJson(valuesJson = """{ }""")

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en")
            val blocks = result.blocks

            assertEquals(true, blocks[0].inlineWithNext)
            assertEquals(false, blocks[1].inlineWithNext)
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

    private fun firstProfileJson(
        valuesJson: String,
        baselineId: String = "example-policy",
        baselineVersion: String = "1.0",
        id: String = "example-policy-default",
        version: String = "1.0",
    ): String =
        """
        {
          "kind": "profile",
          "id": "$id",
          "version": "$version",
          "parent": { "id": "$baselineId", "version": "$baselineVersion", "hash": "sha256:0102" },
          "overrides": {
            "values": $valuesJson
          }
        }
        """.trimIndent()

    private fun secondProfileJson(
        valuesJson: String,
        parentProfileId: String = "example-policy-default",
        parentProfileVersion: String = "1.0",
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
              "kind": "profile",
              "id": "example-policy-operator",
              "version": "1.0",
              "parent": { "id": "$parentProfileId", "version": "$parentProfileVersion", "hash": "sha256:0102" },
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
                    Locale(
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
                        decimalSeparator = ".",
                        groupingSeparator = ",",
                        atWord = "at",
                        patterns =
                            Locale.Patterns(
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
                        duration = Locale.DurationTexts(infinite = "indefinitely"),
                    ),
                "de" to
                    Locale(
                        language = "de",
                        months =
                            listOf(
                                "Januar",
                                "Februar",
                                "März",
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
                        decimalSeparator = ",",
                        groupingSeparator = ".",
                        atWord = "um",
                        patterns =
                            Locale.Patterns(
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
                        duration =
                            Locale.DurationTexts(
                                infinite = "unbegrenzt",
                                units =
                                    mapOf(
                                        "s" to Locale.DurationTexts.UnitTexts("Sekunde", "Sekunden"),
                                        "m" to Locale.DurationTexts.UnitTexts("Minute", "Minuten"),
                                        "h" to Locale.DurationTexts.UnitTexts("Stunde", "Stunden"),
                                        "d" to Locale.DurationTexts.UnitTexts("Tag", "Tage"),
                                        "w" to Locale.DurationTexts.UnitTexts("Woche", "Wochen"),
                                        "y" to Locale.DurationTexts.UnitTexts("Jahr", "Jahre"),
                                    ),
                            ),
                    ),
            )

        override fun resolve(languageTag: String): Locale {
            val primary = languageTag.substringBefore('-').substringBefore('_').lowercase()
            return locales[primary] ?: locales.getValue("en")
        }
    }
}
