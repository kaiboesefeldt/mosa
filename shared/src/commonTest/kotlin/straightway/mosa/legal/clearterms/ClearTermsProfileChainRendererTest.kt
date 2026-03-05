package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsProfileChainRendererTest {
    private val hasher = SequenceHashProvider()
    private val encoder = FixedTextEncoder()
    private val localeProvider = TestClearTermsLocaleProvider()
    private val renderer = ClearTermsRenderer(ClearTermsJsonParser(), hasher, encoder, localeProvider = localeProvider)

    @Test
    fun `renders final text for profile chain`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [
                      { "id": "operator", "text": "Operator: {{string:operator_name}}." },
                      { "id": "limit", "text": "Limit: {{number:max_records??unknown}}." }
                    ]
                  }
                }
                """.trimIndent()
            val profileA =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org A",
                      "max_records": 10
                    }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-b",
                  "version": "1.0",
                  "parent": { "id": "example-policy-a", "version": "1.0", "hash": "sha256:02" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org B",
                      "max_records": null
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(profileA, profileB), "en")

            assertEquals("Operator: Org B.Limit: unknown.", result)
        }

    @Test
    fun `rejects empty profile chain`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "operator", "text": "Operator: {{string:operator_name}}." } ]
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, emptyList(), "en")
                }

            assertEquals("At least one profile is required", exception.message)
        }

    @Test
    fun `rejects unresolved null baseline block in final text`() =
        runTest {
            hasher.reset()
            val baseline =
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
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, listOf(profile), "en")
                }

            assertEquals("Text block 'contact' is unresolved for 'en'", exception.message)
        }

    @Test
    fun `renders when profile replaces null baseline block`() =
        runTest {
            hasher.reset()
            val baseline =
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
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "contact": "Contact: support@example.org." },
                        "append": []
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")

            assertEquals("Contact: support@example.org.", result)
        }

    @Test
    fun `renders profile chain when profile omits values and only overrides text`() =
        runTest {
            hasher.reset()
            val baseline =
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
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "texts": {
                      "en": {
                        "replace": { "contact": "Contact only text override." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")

            assertEquals("Contact only text override.", result)
        }

    @Test
    fun `rejects parent mismatch in profile chain`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "operator", "text": "Operator: {{string:operator_name}}." } ]
                  }
                }
                """.trimIndent()
            val profileA =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "values": { "operator_name": "Org A" }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-b",
                  "version": "1.0",
                  "parent": { "id": "wrong-id", "version": "1.0", "hash": "sha256:02" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, listOf(profileA, profileB), "en")
                }

            assertEquals("Reference id mismatch", exception.message)
        }

    @Test
    fun `rejects missing language in profile chain rendering`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "operator", "text": "Operator: {{string:operator_name}}." } ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "values": { "operator_name": "Org A" }
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, listOf(profile), "de")
                }

            assertEquals("Language 'de' not available", exception.message)
        }

    @Test
    fun `renders only final language subset after chained profile text overrides`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "de": [ { "id": "main", "text": "DE" } ],
                    "en": [ { "id": "main", "text": "EN" } ],
                    "fr": [ { "id": "main", "text": "FR" } ],
                    "zh": [ { "id": "main", "text": "ZH" } ]
                  }
                }
                """.trimIndent()
            val profileA =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "texts": {
                      "de": { "replace": { "main": "DE 1" } },
                      "en": { "replace": { "main": "EN 1" } },
                      "zh": { "replace": { "main": "ZH 1" } }
                    }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-b",
                  "version": "1.0",
                  "parent": { "id": "example-policy-a", "version": "1.0", "hash": "sha256:02" },
                  "overrides": {
                    "texts": {
                      "en": { "replace": { "main": "EN 2" } },
                      "zh": { "replace": { "main": "ZH 2" } }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profileA, profileB))

            assertEquals(setOf("en", "zh"), result.textsByLanguage.keys)
            assertEquals("EN 2", result.textsByLanguage.getValue("en"))
        }

    @Test
    fun `rejects profile chain when no language remains after text overrides`() =
        runTest {
            hasher.reset()
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "example-policy",
                  "version": "1.0",
                  "texts": {
                    "en": [ { "id": "main", "text": "EN" } ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "example-policy-a",
                  "version": "1.0",
                  "parent": { "id": "example-policy", "version": "1.0", "hash": "sha256:01" },
                  "overrides": {
                    "texts": {}
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderAll(baseline, listOf(profile))
                }

            assertEquals("No languages available after applying profile chain", exception.message)
        }

    private class SequenceHashProvider : ClearTermsHashProvider {
        private var index = 0

        override suspend fun sha256(data: ByteArray): ByteArray {
            index += 1
            return byteArrayOf(index.toByte())
        }

        fun reset() {
            index = 0
        }
    }

    private class FixedTextEncoder : ClearTermsTextEncoder {
        override fun encode(value: String): ByteArray = value.encodeToByteArray()
    }

    private class TestClearTermsLocaleProvider : ClearTermsLocaleProvider {
        override fun resolve(languageTag: String): ClearTermsLocale =
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
            )
    }
}
