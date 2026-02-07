package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsRendererTest {
    private val hasher = FixedHashProvider(byteArrayOf(0x01, 0x02))
    private val renderer = ClearTermsRenderer(ClearTermsJsonParser(), hasher)

    @Test
    fun `renders baseline default and deviation`() = runTest {
        val baseline = """
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
        val defaultProfile = """
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
        val deviation = """
{
  "kind": "deviation",
  "id": "example-policy-operator",
  "version": "1.0",
  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
  "overrides": { "values": {} }
}
""".trimIndent()

        val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

        val expected = """
Retention: as long as necessary.
Operator: Example Organization.
""".trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `applies deviation value overrides`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "retention_period": "30d" }
}
""".trimIndent()
        val deviation = """
{
  "kind": "deviation",
  "id": "example-policy-operator",
  "version": "1.0",
  "default": { "id": "example-policy-default", "version": "1.0", "hash": "sha256:0102" },
  "overrides": { "values": { "retention_period": "infinite" } }
}
""".trimIndent()

        val result = renderer.renderLanguage(baseline, defaultProfile, deviation, "en")

        val expected = """
Retention: infinite.
""".trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `applies text overrides with alignment`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "support_day": "mon" }
}
""".trimIndent()
        val deviation = """
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

        val expected = """
Support: mon.
Note: responses vary.
Additional terms.
""".trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `rejects missing placeholder value`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": {}
}
""".trimIndent()
        val deviation = """
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
    fun `rejects unused values`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "operator_name": "Example Organization", "extra_value": 12 }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects invalid reference hash`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:FFFF" },
  "values": { "operator_name": "Example Organization" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects baseline block misalignment`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": {}
}
""".trimIndent()
        val deviation = """
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
    fun `rejects deviation texts missing language`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "support_day": "mon" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects replace of missing block`() = runTest {
        val baseline = """
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ]
  }
}
""".trimIndent()
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "support_day": "mon" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects append id collision`() = runTest {
        val baseline = """
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [ { "id": "support", "text": "Support: {{weekday:support_day??business days}}." } ]
  }
}
""".trimIndent()
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "support_day": "mon" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects placeholder type mismatch`() = runTest {
        val baseline = """
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [ { "id": "limit", "text": "Limit: {{number:max_records??a limited number}}." } ]
  }
}
""".trimIndent()
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "max_records": "not-a-number" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects null without fallback`() = runTest {
        val baseline = """
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [ { "id": "retention", "text": "Retention: {{duration:retention_period}}." } ]
  }
}
""".trimIndent()
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "retention_period": null }
}
""".trimIndent()
        val deviation = """
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
    fun `renders escaped braces`() = runTest {
        val baseline = """
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
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "operator_name": "Example" }
}
""".trimIndent()
        val deviation = """
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
    fun `rejects invalid time value`() = runTest {
        val baseline = """
{
  "kind": "baseline",
  "id": "example-policy",
  "version": "1.0",
  "texts": {
    "en": [ { "id": "start", "text": "Start: {{time:start_time??soon}}." } ]
  }
}
""".trimIndent()
        val defaultProfile = """
{
  "kind": "default",
  "id": "example-policy-default",
  "version": "1.0",
  "baseline": { "id": "example-policy", "version": "1.0", "hash": "sha256:0102" },
  "values": { "start_time": "2025-01-02T10:00:00" }
}
""".trimIndent()
        val deviation = """
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
}

private class FixedHashProvider(private val bytes: ByteArray) : ClearTermsHashProvider {
    override suspend fun sha256(data: ByteArray): ByteArray = bytes
}
