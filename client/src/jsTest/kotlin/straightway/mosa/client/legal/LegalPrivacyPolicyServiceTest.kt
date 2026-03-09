package straightway.mosa.client.legal

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.legal.clearterms.ClearTermsRenderBackground
import straightway.mosa.legal.clearterms.ClearTermsRenderResult
import straightway.mosa.legal.clearterms.ClearTermsRenderedBlock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class LegalPrivacyPolicyServiceTest {
    @Test
    fun `loads baseline and all privacy profiles from pod`() = runTest {
        val podAccess = RecordingPrivacyPodAccess()
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { podAccess },
                preferredLanguageTagsProvider = { listOf("de-DE") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("de" to "# Datenschutz"))),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
        assertEquals(
            listOf("baseline", "default", "mosa", "operator"),
            podAccess.privacyCalls,
        )
    }

    @Test
    fun `falls back to english when preferred language is unavailable`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("fr-FR") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("en" to "# Privacy"))),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Privacy</h1>")
    }

    @Test
    fun `fails when no pod base exists`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider(""),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("de" to "# Datenschutz"))),
            )

        assertFailsWith<IllegalStateException> {
            service.loadPrivacyPolicyHtml()
        }
    }

    @Test
    fun `keeps full profile chain including duplicate operator profile`() = runTest {
        val podAccess = RecordingPrivacyPodAccess(operator = "mosa")
        val seenProfiles = mutableListOf<List<String>>()
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { podAccess },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    object : PrivacyClearTermsContentProvider {
                        override suspend fun renderedTextsByLanguage(
                            baselineJson: String,
                            profileJsons: List<String>,
                        ): ClearTermsRenderResult {
                            seenProfiles += profileJsons
                            return renderResultOf(mapOf("de" to "# Datenschutz"))
                        }
                    },
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
        assertContains(seenProfiles, listOf("default", "mosa", "mosa"))
    }

    @Test
    fun `renders highlighted placeholder segment as yellow span`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("Operator: ", ClearTermsRenderBackground.NONE),
                                                        ClearTermsRenderedBlock.Segment("Org A", ClearTermsRenderBackground.YELLOW),
                                                        ClearTermsRenderedBlock.Segment(".", ClearTermsRenderBackground.NONE),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(
            html,
            """Operator: <span data-ct-block-id="main" class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">Org A</span>.""",
        )
    }

    @Test
    fun `renders uncolored overridden placeholder segment with baseline marker span`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("In ", ClearTermsRenderBackground.NONE),
                                                        ClearTermsRenderedBlock.Segment(
                                                            "Mosa",
                                                            ClearTermsRenderBackground.NONE,
                                                            isBaselineOverride = true,
                                                        ),
                                                        ClearTermsRenderedBlock.Segment(" werden Daten genutzt.", ClearTermsRenderBackground.NONE),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, """In <span class="legal-ct-baseline-diff">Mosa</span> werden Daten genutzt.""")
    }

    @Test
    fun `fails when clearterms provider returns no texts`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(emptyMap())),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                service.loadPrivacyPolicyHtml()
            }

        assertEquals("No ClearTerms text available", exception.message)
    }

    @Test
    fun `uses primary language fallback from region preference`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de-DE") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("de" to "# Datenschutz"))),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
    }

    @Test
    fun `falls back to alphabetically first language when no preferred and no english exists`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("it-IT") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            mapOf(
                                "fr" to "# Confidentialite",
                                "de" to "# Datenschutz",
                            ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
    }

    @Test
    fun `wraps block in background div when full block has non default background`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("Block text", ClearTermsRenderBackground.RED),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(
            html,
            """<div class="legal-ct-block legal-ct-bg-red"""",
        )
        assertContains(
            html,
            """<span data-ct-block-id="main" class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">Block text</span>""",
        )
    }

    @Test
    fun `does not wrap block when colored segments are mixed`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("A", ClearTermsRenderBackground.RED),
                                                        ClearTermsRenderedBlock.Segment("B", ClearTermsRenderBackground.YELLOW),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(
            html,
            """<span data-ct-block-id="main" class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">A</span><span data-ct-block-id="main" class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">B</span>""",
        )
    }

    @Test
    fun `wraps baseline override block with subtle marker class without deviation class`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                isBaselineOverride = true,
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("Changed text", ClearTermsRenderBackground.NONE),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, """<div class="legal-ct-block legal-ct-baseline-diff"""")
    }

    @Test
    fun `renders inlineWithNext block together with following block`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "rights-access-label",
                                                inlineWithNext = true,
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("- Access: ", ClearTermsRenderBackground.NONE),
                                                    ),
                                            ),
                                            ClearTermsRenderedBlock(
                                                id = "rights-access-action-slot-service",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("Download data", ClearTermsRenderBackground.NONE),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<ul><li>Access: Download data</li></ul>")
        assertFalse(html.contains("<p>Download data</p>"))
    }

    @Test
    fun `returns uncritical comparison text from state before first highlighted state`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult =
                    when (profileJsons.size) {
                        0,
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org A."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Operator: Org A.",
                                                                background = ClearTermsRenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            )

                        else ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org B."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Operator: Org B.",
                                                                background = ClearTermsRenderBackground.RED,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            )
                    }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess(baseline = "baseline", default = "profile-a", mosa = "profile-b", operator = "profile-b") },
                preferredLanguageTagsProvider = { listOf("en") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertEquals("Operator: Org A.", document.uncriticalTextByBlockId["operator"])
    }

    @Test
    fun `uses baseline block text when block is already highlighted in first profile step`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult =
                    when (profileJsons.size) {
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Service: Baseline text"),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "service",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Service: Baseline text",
                                                                background = ClearTermsRenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            )

                        2,
                        3 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Service: Final text"),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "service",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Service: Final text",
                                                                background = ClearTermsRenderBackground.RED,
                                                            ),
                                                    ),
                                                ),
                                            ),
                                    ),
                            )

                        else -> throw IllegalStateException("unexpected profile count")
                    }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess(baseline = "baseline", default = "profile-a", mosa = "profile-b", operator = "profile-c") },
                preferredLanguageTagsProvider = { listOf("en") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertEquals("Service: Baseline text", document.uncriticalTextByBlockId["service"])
    }

    @Test
    fun `derives uncritical text from profile chain even when intermediate renders are unavailable`() = runTest {
        val calls = mutableListOf<Int>()
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult {
                    calls += profileJsons.size
                    if (profileJsons.size != 3) {
                        throw IllegalStateException("intermediate render not available")
                    }
                    return renderResultOf(
                        textsByLanguage = mapOf("en" to "Service: Operator"),
                        blocksByLanguage =
                            mapOf(
                                "en" to
                                    listOf(
                                        ClearTermsRenderedBlock(
                                            id = "service",
                                            segments =
                                                listOf(
                                                    ClearTermsRenderedBlock.Segment(
                                                        text = "Service: Operator",
                                                        background = ClearTermsRenderBackground.RED,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    )
                }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory =
                    {
                        RecordingPrivacyPodAccess(
                            baseline = minimalBaselineCanonicalJson(),
                            default = minimalDefaultProfileCanonicalJson(),
                            mosa = minimalMosaProfileCanonicalJson(),
                            operator = minimalOperatorProfileCanonicalJson(),
                        )
                    },
                preferredLanguageTagsProvider = { listOf("en") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertEquals(listOf(3), calls)
        assertEquals("Service: Mosa", document.uncriticalTextByBlockId["service"])
    }

    @Test
    fun `renders localized duration in uncritical text from profile chain`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult {
                    if (profileJsons.size != 3) {
                        throw IllegalStateException("intermediate render not available")
                    }
                    return renderResultOf(
                        textsByLanguage = mapOf("de" to "Aufbewahrung: 30 Tage."),
                        blocksByLanguage =
                            mapOf(
                                "de" to
                                    listOf(
                                        ClearTermsRenderedBlock(
                                            id = "retention",
                                            segments =
                                                listOf(
                                                    ClearTermsRenderedBlock.Segment(
                                                        text = "Aufbewahrung: 30 Tage.",
                                                        background = ClearTermsRenderBackground.YELLOW,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    )
                }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory =
                    {
                        RecordingPrivacyPodAccess(
                            baseline = minimalDurationBaselineCanonicalJson(),
                            default = minimalDurationDefaultProfileCanonicalJson(),
                            mosa = minimalDurationMosaProfileCanonicalJson(),
                            operator = minimalDurationOperatorProfileCanonicalJson(),
                        )
                    },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertEquals("Aufbewahrung: 14 Tage.", document.uncriticalTextByBlockId["retention"])
    }

    @Test
    fun `renders block id attribute on clickable highlighted marker`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess() },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    fixedContentProvider(
                        renderResultOf(
                            textsByLanguage = mapOf("de" to "ignored"),
                            blocksByLanguage =
                                mapOf(
                                    "de" to
                                        listOf(
                                            ClearTermsRenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        ClearTermsRenderedBlock.Segment("Hinweis ", ClearTermsRenderBackground.NONE),
                                                        ClearTermsRenderedBlock.Segment("kritisch", ClearTermsRenderBackground.RED),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                    ),
            )

        val document = service.loadPrivacyPolicyDocument()

        assertContains(document.html, """data-ct-block-id="main"""")
        assertNotNull(document.uncriticalTextByBlockId["main"])
    }

    @Test
    fun `replaces unresolved placeholders in uncritical text with ellipsis`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult =
                    when (profileJsons.size) {
                        0,
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: {{string:operator_name}}."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Operator: {{string:operator_name}}.",
                                                                background = ClearTermsRenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            )

                        else ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org B."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                ClearTermsRenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            ClearTermsRenderedBlock.Segment(
                                                                text = "Operator: Org B.",
                                                                background = ClearTermsRenderBackground.RED,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            )
                    }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess(baseline = "baseline", default = "profile-a", mosa = "profile-b", operator = "profile-b") },
                preferredLanguageTagsProvider = { listOf("en") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertEquals("Operator: ....", document.uncriticalTextByBlockId["operator"])
    }

    @Test
    fun `keeps final rendering stable when intermediate chain step cannot be rendered`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedTextsByLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                ): ClearTermsRenderResult {
                    if (profileJsons.size == 1) {
                        throw IllegalStateException("Text block 'service-description-slot-service' is unresolved for 'de'")
                    }
                    return renderResultOf(
                        textsByLanguage = mapOf("de" to "Finaler Text"),
                        blocksByLanguage =
                            mapOf(
                                "de" to
                                    listOf(
                                        ClearTermsRenderedBlock(
                                            id = "service-description-slot-service",
                                            segments =
                                                listOf(
                                                    ClearTermsRenderedBlock.Segment(
                                                        text = "Finaler Text",
                                                        background = ClearTermsRenderBackground.RED,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    )
                }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { RecordingPrivacyPodAccess(baseline = "baseline", default = "profile-a", mosa = "profile-b", operator = "profile-b") },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider = contentProvider,
            )

        val document = service.loadPrivacyPolicyDocument()

        assertContains(document.html, "Finaler Text")
        assertEquals("", document.uncriticalTextByBlockId["service-description-slot-service"])
    }
}

private fun fixedPodBaseProvider(base: String): PodBaseProvider =
    PodBaseProvider {
        val globalThis = js("globalThis")
        globalThis.MOSA_POD_BASE = base
        null
    }

private fun fixedContentProvider(result: ClearTermsRenderResult): PrivacyClearTermsContentProvider =
    object : PrivacyClearTermsContentProvider {
        override suspend fun renderedTextsByLanguage(
            baselineJson: String,
            profileJsons: List<String>,
        ): ClearTermsRenderResult = result
    }

private fun renderResultOf(
    textsByLanguage: Map<String, String>,
    blocksByLanguage: Map<String, List<ClearTermsRenderedBlock>> = emptyMap(),
): ClearTermsRenderResult = ClearTermsRenderResult(textsByLanguage, blocksByLanguage)

private fun minimalBaselineCanonicalJson(): String =
    """
    {"kind":"baseline","id":"base","version":"1","texts":{"en":[{"id":"service","text":"Service: {{string:SERVICE_DISPLAY_NAME}}","hints":{"override":"once"}}]}}
    """.trimIndent()

private fun minimalDefaultProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"default","version":"1","parent":{"id":"base","version":"1","hash":"sha256:base"},"overrides":{"values":{}}}
    """.trimIndent()

private fun minimalMosaProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"mosa","version":"1","parent":{"id":"default","version":"1","hash":"sha256:default"},"overrides":{"texts":{"en":{"replace":{"service":"Service: Mosa"}}}}}
    """.trimIndent()

private fun minimalOperatorProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"operator","version":"1","parent":{"id":"mosa","version":"1","hash":"sha256:mosa"},"overrides":{"texts":{"en":{"replace":{"service":"Service: Operator"}}}}}
    """.trimIndent()

private fun minimalDurationBaselineCanonicalJson(): String =
    """
    {"kind":"baseline","id":"base-duration","version":"1","texts":{"de":[{"id":"retention","text":"Aufbewahrung: {{duration:RETENTION_OPERATIONAL_LOG_DATA}}."}]}}
    """.trimIndent()

private fun minimalDurationDefaultProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"default-duration","version":"1","parent":{"id":"base-duration","version":"1","hash":"sha256:base-duration"},"overrides":{"values":{"RETENTION_OPERATIONAL_LOG_DATA":"14d"}}}
    """.trimIndent()

private fun minimalDurationMosaProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"mosa-duration","version":"1","parent":{"id":"default-duration","version":"1","hash":"sha256:default-duration"},"overrides":{"values":{"RETENTION_OPERATIONAL_LOG_DATA":"30d"}}}
    """.trimIndent()

private fun minimalDurationOperatorProfileCanonicalJson(): String =
    """
    {"kind":"profile","id":"operator-duration","version":"1","parent":{"id":"mosa-duration","version":"1","hash":"sha256:mosa-duration"},"overrides":{"values":{"RETENTION_OPERATIONAL_LOG_DATA":"30d"}}}
    """.trimIndent()

private class RecordingPrivacyPodAccess(
    private val baseline: String = "baseline",
    private val default: String = "default",
    private val mosa: String = "mosa",
    private val operator: String = "operator",
) : PodAccess {
    val privacyCalls = mutableListOf<String>()

    override suspend fun approveGid(gid: String): PodCertificateResponse {
        error("not used")
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        error("not used")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("not used")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("not used")
    }

    override suspend fun deletePosts(jwt: String): DeletePostsResult {
        error("not used")
    }

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult {
        error("not used")
    }

    override suspend fun exportPostsCsv(jwt: String): ByteArray {
        error("not used")
    }

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray {
        error("not used")
    }

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult {
        error("not used")
    }

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult {
        error("not used")
    }

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult {
        error("not used")
    }

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult {
        error("not used")
    }

    override suspend fun privacyBaselineClearTerms(): String {
        privacyCalls += "baseline"
        return baseline
    }

    override suspend fun privacyDefaultClearTerms(): String {
        privacyCalls += "default"
        return default
    }

    override suspend fun privacyMosaClearTerms(): String {
        privacyCalls += "mosa"
        return mosa
    }

    override suspend fun privacyOperatorClearTerms(): String {
        privacyCalls += "operator"
        return operator
    }
}
