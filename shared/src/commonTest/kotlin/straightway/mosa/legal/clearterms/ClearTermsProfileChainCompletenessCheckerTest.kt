package straightway.mosa.legal.clearterms

import straightway.mosa.legal.clearterms.validation.ClearTermsProfileChainCompletenessChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClearTermsProfileChainCompletenessCheckerTest {
    private val checker = ClearTermsProfileChainCompletenessChecker()

    @Test
    fun `returns incomplete with skeleton when chain is missing required overrides`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "de":[
                  {"id":"slot","text":null,"hints":{"expectedContent":"Fill this section","override":"once"}},
                  {"id":"contact","text":"Kontakt: {{string:CONTACT_EMAIL}}"}
                ],
                "en":[
                  {"id":"slot","text":null,"hints":{"expectedContent":"Fill this section","override":"once"}},
                  {"id":"contact","text":"Contact: {{string:CONTACT_EMAIL}}"}
                ]
              }
            }
            """.trimIndent()
        val defaultCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"default-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{"SERVICE_DISPLAY_NAME":"Mosa"}}
            }
            """.trimIndent()
        val serviceCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"service-1",
              "version":"1.0",
              "parent":{"id":"default-1","version":"1.0","hash":"sha256:bb"},
              "overrides":{"values":{}}
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(defaultCanonicalJson, serviceCanonicalJson),
            )

        assertFalse(result.complete)
        assertEquals(listOf("CONTACT_EMAIL"), result.missingPlaceholderKeys)
        assertEquals(listOf("slot"), result.unresolvedRequiredBlockIdsByLanguage.getValue("de"))
        assertEquals(listOf("slot"), result.unresolvedRequiredBlockIdsByLanguage.getValue("en"))
        assertTrue(result.skeletonYaml.contains("CONTACT_EMAIL: null # type: string"))
        assertTrue(result.skeletonYaml.contains("slot: null # TODO: Fill this section"))
    }

    @Test
    fun `returns complete when chain resolves required texts and values`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "de":[
                  {"id":"slot","text":null,"hints":{"expectedContent":"Fill this section","override":"once"}},
                  {"id":"contact","text":"Kontakt: {{string:CONTACT_EMAIL}}"}
                ],
                "en":[
                  {"id":"slot","text":null,"hints":{"expectedContent":"Fill this section","override":"once"}},
                  {"id":"contact","text":"Contact: {{string:CONTACT_EMAIL}}"}
                ]
              }
            }
            """.trimIndent()
        val defaultCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"default-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{}}
            }
            """.trimIndent()
        val serviceCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"service-1",
              "version":"1.0",
              "parent":{"id":"default-1","version":"1.0","hash":"sha256:bb"},
              "overrides":{
                "values":{"CONTACT_EMAIL":"privacy@example.org"},
                "texts":{
                  "de":{"replace":{"slot":"Pflichttext."}},
                  "en":{"replace":{"slot":"Required text."}}
                }
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(defaultCanonicalJson, serviceCanonicalJson),
            )

        assertTrue(result.complete)
        assertTrue(result.missingPlaceholderKeys.isEmpty())
        assertEquals(emptyList(), result.unresolvedRequiredBlockIdsByLanguage.getValue("de"))
        assertEquals(emptyList(), result.unresolvedRequiredBlockIdsByLanguage.getValue("en"))
    }

    @Test
    fun `returns complete when baseline is complete without profiles`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"contact","text":"Contact: example.org"}
                ]
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = emptyList(),
            )

        assertTrue(result.complete)
        assertTrue(result.missingPlaceholderKeys.isEmpty())
        assertTrue(result.skeletonYaml.contains("""id: "TODO_PROFILE_ID""""))
        assertTrue(result.skeletonYaml.contains("""version: "TODO_PROFILE_VERSION""""))
        assertTrue(result.skeletonYaml.contains("""parent:"""))
        assertTrue(result.skeletonYaml.contains("""id: "baseline-1""""))
    }

    @Test
    fun `treats explicit null value as missing when placeholder has no fallback`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"contact","text":"Contact: {{string:CONTACT_EMAIL}}"}
                ]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{"CONTACT_EMAIL":null}}
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(profileCanonicalJson),
            )

        assertFalse(result.complete)
        assertEquals(listOf("CONTACT_EMAIL"), result.missingPlaceholderKeys)
        assertTrue(result.skeletonYaml.contains("CONTACT_EMAIL: null # type: string"))
    }

    @Test
    fun `accepts explicit null value when placeholder always defines fallback`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"contact","text":"Contact: {{string:CONTACT_EMAIL??unknown}}"}
                ]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{"CONTACT_EMAIL":null}}
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(profileCanonicalJson),
            )

        assertTrue(result.complete)
        assertTrue(result.missingPlaceholderKeys.isEmpty())
    }

    @Test
    fun `accepts explicit null when same placeholder appears multiple times with fallback`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"one","text":"A: {{string:CONTACT_EMAIL??unknown}}"},
                  {"id":"two","text":"B: {{string:CONTACT_EMAIL??not provided}}"}
                ]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{"CONTACT_EMAIL":null}}
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(profileCanonicalJson),
            )

        assertTrue(result.complete)
        assertTrue(result.missingPlaceholderKeys.isEmpty())
    }

    @Test
    fun `treats explicit null as missing when same placeholder has mixed fallback usage`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"one","text":"A: {{string:CONTACT_EMAIL??unknown}}"},
                  {"id":"two","text":"B: {{string:CONTACT_EMAIL}}"}
                ]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{"values":{"CONTACT_EMAIL":null}}
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(profileCanonicalJson),
            )

        assertFalse(result.complete)
        assertEquals(listOf("CONTACT_EMAIL"), result.missingPlaceholderKeys)
    }

    @Test
    fun `fails when same placeholder key is used with different types`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"one","text":"A: {{string:SHARED_KEY}}"},
                  {"id":"two","text":"B: {{number:SHARED_KEY}}"}
                ]
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                checker.analyze(
                    baselineCanonicalJson = baselineCanonicalJson,
                    profileChainCanonicalJsons = emptyList(),
                )
            }

        assertEquals("Placeholder type mismatch for 'SHARED_KEY'", exception.message)
    }

    @Test
    fun `uses default todo hint when required block has no expected content hint`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"policy","text":null,"hints":{"override":"once"}}
                ]
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = emptyList(),
            )

        assertTrue(result.skeletonYaml.contains("policy: null # TODO: Provide required service-specific text."))
    }

    @Test
    fun `normalizes multiline expected content hint into single line todo`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"policy","text":null,"hints":{"expectedContent":"First line\nSecond line\rThird line","override":"once"}}
                ]
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = emptyList(),
            )

        assertTrue(result.skeletonYaml.contains("policy: null # TODO: First line Second line Third line"))
    }

    @Test
    fun `uses default todo hint when required block has no hints object`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"policy","text":null}
                ]
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = emptyList(),
            )

        assertTrue(result.skeletonYaml.contains("policy: null # TODO: Provide required service-specific text."))
    }

    @Test
    fun `handles profile with text overrides but without values map`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[
                  {"id":"slot","text":null,"hints":{"expectedContent":"Fill slot","override":"once"}},
                  {"id":"contact","text":"Contact: {{string:CONTACT_EMAIL}}"}
                ]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{
                "texts":{
                  "en":{"replace":{"slot":"Resolved text."}}
                }
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(profileCanonicalJson),
            )

        assertFalse(result.complete)
        assertEquals(listOf("CONTACT_EMAIL"), result.missingPlaceholderKeys)
        assertEquals(emptyList(), result.unresolvedRequiredBlockIdsByLanguage.getValue("en"))
    }

    @Test
    fun `shrinks supported language set when profiles provide only subsets`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "de":[{"id":"main","text":"DE"}],
                "en":[{"id":"main","text":"EN"}],
                "fr":[{"id":"main","text":"FR"}],
                "zh":[{"id":"main","text":"ZH"}]
              }
            }
            """.trimIndent()
        val firstProfileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{
                "texts":{
                  "de":{"replace":{"main":"DE 1"}},
                  "en":{"replace":{"main":"EN 1"}},
                  "zh":{"replace":{"main":"ZH 1"}}
                }
              }
            }
            """.trimIndent()
        val secondProfileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-2",
              "version":"1.0",
              "parent":{"id":"profile-1","version":"1.0","hash":"sha256:bb"},
              "overrides":{
                "texts":{
                  "en":{"replace":{"main":"EN 2"}},
                  "zh":{"replace":{"main":"ZH 2"}}
                }
              }
            }
            """.trimIndent()

        val result =
            checker.analyze(
                baselineCanonicalJson = baselineCanonicalJson,
                profileChainCanonicalJsons = listOf(firstProfileCanonicalJson, secondProfileCanonicalJson),
            )

        assertTrue(result.complete)
        assertEquals(setOf("en", "zh"), result.unresolvedRequiredBlockIdsByLanguage.keys)
    }

    @Test
    fun `fails when no language remains at end of profile chain`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "en":[{"id":"main","text":"EN"}]
              }
            }
            """.trimIndent()
        val profileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{
                "texts":{}
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                checker.analyze(
                    baselineCanonicalJson = baselineCanonicalJson,
                    profileChainCanonicalJsons = listOf(profileCanonicalJson),
                )
            }

        assertEquals("No languages available after applying profile chain", exception.message)
    }

    @Test
    fun `fails when a profile introduces language outside previous subset`() {
        val baselineCanonicalJson =
            """
            {
              "kind":"baseline",
              "id":"baseline-1",
              "version":"1.0",
              "texts":{
                "de":[{"id":"main","text":"DE"}],
                "en":[{"id":"main","text":"EN"}]
              }
            }
            """.trimIndent()
        val firstProfileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-1",
              "version":"1.0",
              "parent":{"id":"baseline-1","version":"1.0","hash":"sha256:aa"},
              "overrides":{
                "texts":{
                  "en":{"replace":{"main":"EN 1"}}
                }
              }
            }
            """.trimIndent()
        val secondProfileCanonicalJson =
            """
            {
              "kind":"profile",
              "id":"profile-2",
              "version":"1.0",
              "parent":{"id":"profile-1","version":"1.0","hash":"sha256:bb"},
              "overrides":{
                "texts":{
                  "de":{"replace":{"main":"DE 2"}}
                }
              }
            }
            """.trimIndent()

        val exception =
            assertFailsWith<ClearTermsValidationException> {
                checker.analyze(
                    baselineCanonicalJson = baselineCanonicalJson,
                    profileChainCanonicalJsons = listOf(firstProfileCanonicalJson, secondProfileCanonicalJson),
                )
            }

        assertEquals("Override language 'de' is not supported by parent profile", exception.message)
    }
}
