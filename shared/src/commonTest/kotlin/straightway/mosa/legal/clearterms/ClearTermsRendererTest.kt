package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsRendererTest {
    private val hasher = FixedHashProvider(byteArrayOf(0x01, 0x02))
    private val encoder = FixedTextEncoder()
    private val renderer = ClearTermsRenderer(ClearTermsJsonParser(), hasher, encoder)

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
Support: mon.
Note: responses vary.
Additional terms.
                """.trimIndent()
            assertEquals(expected, result)
        }

    @Test
    fun `rejects deviation text with fallback placeholders`() =
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

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
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
    fun `rejects fallbacks in deviation text`() =
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

            assertFailsWith<ClearTermsValidationException> {
                renderer.renderLanguage(baseline, defaultProfile, deviation, "en")
            }
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

            assertEquals("Start: 2025-01-02T10:00:00Z.", result)
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

            assertEquals("Support: mon.", result)
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
                """,
  "texts": $textsJson"""
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
}
