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

class ClearTermsRenderHighlightTest {
    private val renderer =
        ClearTermsRenderer(
            parser = JsonParser(),
            hashProvider = IncrementingHashProvider(),
            textEncoder = PassthroughTextEncoder(),
            localeProvider = TestLocaleProvider(),
        )

    @Test
    fun `keeps required placeholder from single profile override uncolored`() =
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(
                listOf(
                    RenderBackground.NONE,
                    RenderBackground.NONE,
                    RenderBackground.NONE,
                ),
                segments.map { it.background },
            )
            assertEquals(listOf(false, true, false), segments.map { it.isBaselineOverride })
            assertEquals("Operator: Org A.", segments.joinToString("") { it.text })
        }

    @Test
    fun `keeps optional placeholder from single profile override uncolored`() =
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(
                listOf(
                    RenderBackground.NONE,
                    RenderBackground.NONE,
                    RenderBackground.NONE,
                ),
                segments.map { it.background },
            )
            assertEquals(listOf(false, true, false), segments.map { it.isBaselineOverride })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.RED), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.YELLOW), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.NONE), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.NONE), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val block =
                result.blocks
                    .single()

            assertEquals(true, block.isBaselineOverride)
            assertEquals(listOf(RenderBackground.NONE), block.segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profileA, profileB), "en")
            val segments = result.blocks.single().segments

            assertEquals(
                listOf(
                    RenderBackground.NONE,
                    RenderBackground.YELLOW,
                    RenderBackground.NONE,
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.NONE), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profileA, profileB), "en")
            val segments = result.blocks.single().segments

            assertEquals(listOf(RenderBackground.RED), segments.map { it.background })
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

            val result = renderer.renderLanguage(baseline, listOf(profile), "en")
            val segments =
                result.blocks
                    .single { it.id == "new-block" }
                    .segments

            assertEquals(listOf(RenderBackground.RED), segments.map { it.background })
            assertEquals("Added block.", segments.single().text)
        }

    @Test
    fun `returns highlighted result for two profile overrides`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org A"
                    }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "policy-secondProfile",
                  "version": "1",
                  "parent": { "id": "policy-default", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org B"
                    }
                  }
                }
                """.trimIndent()

            val result = renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "en")
            val segments = result.blocks.single().segments

            assertEquals(
                listOf(
                    RenderBackground.NONE,
                    RenderBackground.YELLOW,
                    RenderBackground.NONE,
                ),
                segments.map { it.background },
            )
            assertEquals("Operator: Org B.", result.text)
        }

    @Test
    fun `throws when requested language is unavailable in profile chain`() =
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
            val firstProfile =
                """
                {
                  "kind": "profile",
                  "id": "policy-default",
                  "version": "1",
                  "parent": { "id": "policy", "version": "1", "hash": "sha256:01" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org A"
                    }
                  }
                }
                """.trimIndent()
            val secondProfile =
                """
                {
                  "kind": "profile",
                  "id": "policy-secondProfile",
                  "version": "1",
                  "parent": { "id": "policy-default", "version": "1", "hash": "sha256:02" },
                  "overrides": {
                    "values": {
                      "operator_name": "Org B"
                    }
                  }
                }
                """.trimIndent()

            val exception =
                assertFailsWith<ValidationException> {
                    renderer.renderLanguage(baseline, listOf(firstProfile, secondProfile), "de")
                }

            assertEquals("Language 'de' not available", exception.message)
        }

    private class IncrementingHashProvider : HashProvider {
        private var counter = 0

        override suspend fun sha256(data: ByteArray): ByteArray {
            counter += 1
            return byteArrayOf(counter.toByte())
        }
    }

    private class PassthroughTextEncoder : TextEncoder {
        override fun encode(value: String): ByteArray = value.encodeToByteArray()
    }

    private class TestLocaleProvider : LocaleProvider {
        override fun resolve(languageTag: String): Locale =
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
            )
    }
}
