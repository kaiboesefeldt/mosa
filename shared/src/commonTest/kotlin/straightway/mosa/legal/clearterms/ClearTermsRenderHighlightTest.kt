package straightway.mosa.legal.clearterms

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTermsRenderHighlightTest {
    private val renderer =
        ClearTermsRenderer(
            parser = ClearTermsJsonParser(),
            hashProvider = IncrementingHashProvider(),
            textEncoder = PassthroughTextEncoder(),
            localeProvider = TestLocaleProvider(),
        )

    @Test
    fun `marks required placeholder from single profile override as yellow`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": { "operator_name": "Org A" }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(
                listOf(
                    ClearTermsRenderBackground.NONE,
                    ClearTermsRenderBackground.YELLOW,
                    ClearTermsRenderBackground.NONE,
                ),
                segments.map {
                    it.background
                },
            )
            assertEquals("Operator: Org A.", segments.joinToString("") { it.text })
        }

    @Test
    fun `marks optional placeholder from single profile override as yellow`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Limit: {{number:max_records??unknown}}." }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": { "max_records": 10 }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(
                listOf(
                    ClearTermsRenderBackground.NONE,
                    ClearTermsRenderBackground.YELLOW,
                    ClearTermsRenderBackground.NONE,
                ),
                segments.map {
                    it.background
                },
            )
        }

    @Test
    fun `marks text block override of non null baseline as red`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Baseline text." }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "main": "Changed text." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.RED), segments.map { it.background })
            assertEquals("Changed text.", segments.single().text)
        }

    @Test
    fun `marks text block override as yellow when baseline override hint is warn`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Baseline text.", "hints": { "override": "warn" } }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "main": "Changed text." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.YELLOW), segments.map { it.background })
        }

    @Test
    fun `keeps text block override uncolored when baseline override hint is ok`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Baseline text.", "hints": { "override": "ok" } }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "main": "Changed text." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.NONE), segments.map { it.background })
        }

    @Test
    fun `keeps single override of null baseline text block uncolored`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": null }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "main": "Filled text." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.NONE), segments.map { it.background })
        }

    @Test
    fun `marks once text override as baseline override without color`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Baseline text.", "hints": { "override": "once" } }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": { "main": "Changed text." }
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val block =
                result.blocksByLanguage
                    .getValue("en")
                    .single()

            assertEquals(true, block.isBaselineOverride)
            assertEquals(listOf(ClearTermsRenderBackground.NONE), block.segments.map { it.background })
        }

    @Test
    fun `marks placeholder overridden by multiple profiles as yellow`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val profileA =
                """
                {
                  "kind": "profile",
                  "id": "policy-a",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": { "operator_name": "Org A" }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "policy-b",
                  "version": "1",
                  "parent": { "id": "policy-a", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": { "operator_name": "Org B" }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profileA, profileB))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(
                listOf(
                    ClearTermsRenderBackground.NONE,
                    ClearTermsRenderBackground.YELLOW,
                    ClearTermsRenderBackground.NONE,
                ),
                segments.map { it.background },
            )
        }

    @Test
    fun `keeps untouched baseline text block uncolored`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Static baseline text." }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-a",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {}
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.NONE), segments.map { it.background })
            assertEquals("Static baseline text.", segments.single().text)
        }

    @Test
    fun `marks null baseline block overridden by two profiles as red`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": null }
                    ]
                  }
                }
                """.trimIndent()
            val profileA =
                """
                {
                  "kind": "profile",
                  "id": "policy-a",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": { "replace": { "main": "Step one." } }
                    }
                  }
                }
                """.trimIndent()
            val profileB =
                """
                {
                  "kind": "profile",
                  "id": "policy-b",
                  "version": "1",
                  "parent": { "id": "policy-a", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": { "replace": { "main": "Step two." } }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profileA, profileB))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.RED), segments.map { it.background })
            assertEquals("Step two.", segments.single().text)
        }

    @Test
    fun `marks appended text block as red`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Main text." }
                    ]
                  }
                }
                """.trimIndent()
            val profile =
                """
                {
                  "kind": "profile",
                  "id": "policy-a",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {},
                    "texts": {
                      "en": {
                        "replace": {},
                        "append": [
                          { "id": "new-block", "text": "Added block." }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, listOf(profile))
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single { it.id == "new-block" }
                    .segments

            assertEquals(listOf(ClearTermsRenderBackground.RED), segments.map { it.background })
            assertEquals("Added block.", segments.single().text)
        }

    @Test
    fun `supports legacy default deviation overload and returns highlighted result`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "policy-default",
                  "version": "1",
                  "baseline": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "values": {
                    "operator_name": "Org A"
                  }
                }
                """.trimIndent()
            val deviationProfile =
                """
                {
                  "kind": "deviation",
                  "id": "policy-deviation",
                  "version": "1",
                  "default": { "id": "policy-default", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org B"
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderAll(baseline, defaultProfile, deviationProfile)
            val segments =
                result.blocksByLanguage
                    .getValue("en")
                    .single()
                    .segments

            assertEquals(
                listOf(
                    ClearTermsRenderBackground.NONE,
                    ClearTermsRenderBackground.YELLOW,
                    ClearTermsRenderBackground.NONE,
                ),
                segments.map { it.background },
            )
            assertEquals("Operator: Org B.", result.textsByLanguage.getValue("en"))
        }

    @Test
    fun `throws in legacy overload when requested language is unavailable`() =
        runTest {
            val baseline =
                """
                {
                  "kind": "baseline",
                  "id": "policy",
                  "version": "1",
                  "texts": {
                    "en": [
                      { "id": "main", "text": "Operator: {{string:operator_name}}." }
                    ]
                  }
                }
                """.trimIndent()
            val defaultProfile =
                """
                {
                  "kind": "default",
                  "id": "policy-default",
                  "version": "1",
                  "baseline": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "values": {
                    "operator_name": "Org A"
                  }
                }
                """.trimIndent()
            val deviationProfile =
                """
                {
                  "kind": "deviation",
                  "id": "policy-deviation",
                  "version": "1",
                  "default": { "id": "policy-default", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org B"
                    }
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ClearTermsValidationException> {
                    renderer.renderLanguage(baseline, defaultProfile, deviationProfile, "de")
                }

            assertEquals("Language 'de' not available", exception.message)
        }

    private class IncrementingHashProvider : ClearTermsHashProvider {
        private var counter = 0

        override suspend fun sha256(input: ByteArray): ByteArray {
            counter += 1
            return byteArrayOf(counter.toByte())
        }
    }

    private class PassthroughTextEncoder : ClearTermsTextEncoder {
        override fun encode(text: String): ByteArray = text.encodeToByteArray()
    }

    private class TestLocaleProvider : ClearTermsLocaleProvider {
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
