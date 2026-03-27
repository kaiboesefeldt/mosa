package straightway.mosa.client.legal

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
import straightway.mosa.legal.LegalDocumentChainResponse
import straightway.mosa.legal.clearterms.render.RenderBackground
import straightway.mosa.legal.clearterms.render.RenderedLanguage
import straightway.mosa.legal.clearterms.render.RenderedBlock
import straightway.mosa.legal.clearterms.validation.ValidationException
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
    fun `loads baseline and all terms profiles from pod`() = runTest {
        val podAccess = RecordingPrivacyPodAccess()
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { podAccess },
                preferredLanguageTagsProvider = { listOf("de-DE") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("de" to "# AGB"))),
            )

        val document = service.loadTermsDocument()

        assertContains(document.html, "<h1>AGB</h1>")
        assertEquals(
            listOf("baseline", "default", "mosa", "operator"),
            podAccess.termsCalls,
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
        val podAccess =
            RecordingPrivacyPodAccess(
                operator = minimalEmptyProfileCanonicalJson(id = "mosa", parentId = "mosa"),
            )
        val seenProfiles = mutableListOf<List<String>>()
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { podAccess },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider =
                    object : PrivacyClearTermsContentProvider {
                        override suspend fun renderedLanguage(
                            baselineJson: String,
                            profileJsons: List<String>,
                            languageTag: String,
                        ): RenderedLanguage {
                            seenProfiles += profileJsons
                            return renderResultOf(mapOf("de" to "# Datenschutz")).forLanguage(languageTag)
                        }
                    },
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
        assertContains(seenProfiles.map { profiles -> profiles.map(::profileIdOf) }, listOf("default", "mosa", "mosa"))
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
                                            RenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("Operator: ", RenderBackground.NONE),
                                                        RenderedBlock.Segment("Org A", RenderBackground.YELLOW),
                                                        RenderedBlock.Segment(".", RenderBackground.NONE),
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
                                            RenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("In ", RenderBackground.NONE),
                                                        RenderedBlock.Segment(
                                                            "Mosa",
                                                            RenderBackground.NONE,
                                                            isBaselineOverride = true,
                                                        ),
                                                        RenderedBlock.Segment(" werden Daten genutzt.", RenderBackground.NONE),
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
    fun `fails when no language remains after profile text overrides`() = runTest {
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory =
                    {
                        RecordingPrivacyPodAccess(
                            baseline = baselineWithLanguages("en"),
                            default = emptyLanguageOverrideProfile(id = "default", parentId = "baseline"),
                            mosa = emptyLanguageOverrideProfile(id = "mosa", parentId = "default"),
                            operator = emptyLanguageOverrideProfile(id = "operator", parentId = "mosa"),
                        )
                    },
                preferredLanguageTagsProvider = { listOf("de") },
                clearTermsContentProvider = fixedContentProvider(renderResultOf(mapOf("en" to "# Privacy"))),
            )

        val exception =
            assertFailsWith<ValidationException> {
                service.loadPrivacyPolicyHtml()
            }

        assertEquals("No languages available after applying profile chain", exception.message)
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
                podAccessFactory =
                    {
                        RecordingPrivacyPodAccess(
                            baseline = baselineWithLanguages("fr", "de"),
                            default = minimalEmptyProfileCanonicalJson(id = "default", parentId = "baseline"),
                            mosa = minimalEmptyProfileCanonicalJson(id = "mosa", parentId = "default"),
                            operator = minimalEmptyProfileCanonicalJson(id = "operator", parentId = "mosa"),
                        )
                    },
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
                                            RenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("Block text", RenderBackground.RED),
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
                                            RenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("A", RenderBackground.RED),
                                                        RenderedBlock.Segment("B", RenderBackground.YELLOW),
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
                                            RenderedBlock(
                                                id = "main",
                                                isBaselineOverride = true,
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("Changed text", RenderBackground.NONE),
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
                                            RenderedBlock(
                                                id = "rights-access-label",
                                                inlineWithNext = true,
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("- Access: ", RenderBackground.NONE),
                                                    ),
                                            ),
                                            RenderedBlock(
                                                id = "rights-access-action-slot-service",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("Download data", RenderBackground.NONE),
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
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage =
                    when (profileJsons.size) {
                        0,
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org A."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Operator: Org A.",
                                                                background = RenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)

                        else ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org B."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Operator: Org B.",
                                                                background = RenderBackground.RED,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)
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
                            operator = minimalMosaProfileCanonicalJson(),
                        )
                    },
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
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage =
                    when (profileJsons.size) {
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Service: Baseline text"),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "service",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Service: Baseline text",
                                                                background = RenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)

                        2,
                        3 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Service: Final text"),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "service",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Service: Final text",
                                                                background = RenderBackground.RED,
                                                            ),
                                                    ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)

                        else -> throw IllegalStateException("unexpected profile count")
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

        assertEquals("Service: Baseline text", document.uncriticalTextByBlockId["service"])
    }

    @Test
    fun `derives uncritical text from profile chain even when intermediate renders are unavailable`() = runTest {
        val calls = mutableListOf<Int>()
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage {
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
                                        RenderedBlock(
                                            id = "service",
                                            segments =
                                                listOf(
                                                    RenderedBlock.Segment(
                                                        text = "Service: Operator",
                                                        background = RenderBackground.RED,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    ).forLanguage(languageTag)
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

        assertEquals(listOf(3, 1, 2), calls)
        assertEquals("Service: Mosa", document.uncriticalTextByBlockId["service"])
    }

    @Test
    fun `renders localized duration in uncritical text from profile chain`() = runTest {
        val contentProvider =
            object : PrivacyClearTermsContentProvider {
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage {
                    if (profileJsons.size != 3) {
                        throw IllegalStateException("intermediate render not available")
                    }
                    return renderResultOf(
                        textsByLanguage = mapOf("de" to "Aufbewahrung: 30 Tage."),
                        blocksByLanguage =
                            mapOf(
                                "de" to
                                    listOf(
                                        RenderedBlock(
                                            id = "retention",
                                            segments =
                                                listOf(
                                                    RenderedBlock.Segment(
                                                        text = "Aufbewahrung: 30 Tage.",
                                                        background = RenderBackground.YELLOW,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    ).forLanguage(languageTag)
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
                                            RenderedBlock(
                                                id = "main",
                                                segments =
                                                    listOf(
                                                        RenderedBlock.Segment("Hinweis ", RenderBackground.NONE),
                                                        RenderedBlock.Segment("kritisch", RenderBackground.RED),
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
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage =
                    when (profileJsons.size) {
                        0,
                        1 ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: {{string:operator_name}}."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Operator: {{string:operator_name}}.",
                                                                background = RenderBackground.NONE,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)

                        else ->
                            renderResultOf(
                                textsByLanguage = mapOf("en" to "Operator: Org B."),
                                blocksByLanguage =
                                    mapOf(
                                        "en" to
                                            listOf(
                                                RenderedBlock(
                                                    id = "operator",
                                                    segments =
                                                        listOf(
                                                            RenderedBlock.Segment(
                                                                text = "Operator: Org B.",
                                                                background = RenderBackground.RED,
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                            ).forLanguage(languageTag)
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
                            operator = minimalMosaProfileCanonicalJson(),
                        )
                    },
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
                override suspend fun renderedLanguage(
                    baselineJson: String,
                    profileJsons: List<String>,
                    languageTag: String,
                ): RenderedLanguage {
                    if (profileJsons.size == 1) {
                        throw IllegalStateException("Text block 'service-description-slot-service' is unresolved for 'de'")
                    }
                    return renderResultOf(
                        textsByLanguage = mapOf("de" to "Finaler Text"),
                        blocksByLanguage =
                            mapOf(
                                "de" to
                                    listOf(
                                        RenderedBlock(
                                            id = "service-description-slot-service",
                                            segments =
                                                listOf(
                                                    RenderedBlock.Segment(
                                                        text = "Finaler Text",
                                                        background = RenderBackground.RED,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    ).forLanguage(languageTag)
                }
            }
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory =
                    {
                        RecordingPrivacyPodAccess(
                            baseline = baselineWithLanguages("de"),
                            default = minimalEmptyProfileCanonicalJson(id = "default", parentId = "baseline"),
                            mosa = minimalEmptyProfileCanonicalJson(id = "mosa", parentId = "default"),
                            operator = minimalEmptyProfileCanonicalJson(id = "operator", parentId = "mosa"),
                        )
                    },
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

private fun fixedContentProvider(result: StubRenderedLanguageSet): PrivacyClearTermsContentProvider =
    object : PrivacyClearTermsContentProvider {
        override suspend fun renderedLanguage(
            baselineJson: String,
            profileJsons: List<String>,
            languageTag: String,
        ): RenderedLanguage = result.forLanguage(languageTag)
    }

private fun renderResultOf(
    textsByLanguage: Map<String, String>,
    blocksByLanguage: Map<String, List<RenderedBlock>> = emptyMap(),
): StubRenderedLanguageSet = StubRenderedLanguageSet(textsByLanguage, blocksByLanguage)

private data class StubRenderedLanguageSet(
    val textsByLanguage: Map<String, String>,
    val blocksByLanguage: Map<String, List<RenderedBlock>> = emptyMap(),
)

private fun StubRenderedLanguageSet.forLanguage(languageTag: String): RenderedLanguage {
    val normalizedLanguageTag = languageTag.trim().lowercase()
    val textEntry =
        textsByLanguage.entries.firstOrNull { (tag, _) -> tag.trim().lowercase() == normalizedLanguageTag }
            ?: textsByLanguage.entries.singleOrNull()
            ?: error("No stubbed ClearTerms text for '$languageTag'")
    val blocks =
        blocksByLanguage.entries.firstOrNull { (tag, _) -> tag.trim().lowercase() == normalizedLanguageTag }?.value
            ?: emptyList()
    return RenderedLanguage(textEntry.key, textEntry.value, blocks)
}

private fun profileIdOf(profileJson: String): String =
    Regex(""""id"\s*:\s*"([^"]+)"""").find(profileJson)?.groupValues?.get(1) ?: profileJson

private fun baselineWithLanguages(vararg languages: String): String =
    """
    {"kind":"baseline","id":"baseline","version":"1","texts":{${languages.joinToString(",") { language -> """"$language":[{"id":"main","text":"# ${language.uppercase()}"}]""" }}}}
    """.trimIndent()

private fun minimalEmptyProfileCanonicalJson(
    id: String,
    parentId: String,
): String =
    """
    {"kind":"profile","id":"$id","version":"1","parent":{"id":"$parentId","version":"1","hash":"sha256:$parentId"},"overrides":{"values":{}}}
    """.trimIndent()

private fun emptyLanguageOverrideProfile(
    id: String,
    parentId: String,
): String =
    """
    {"kind":"profile","id":"$id","version":"1","parent":{"id":"$parentId","version":"1","hash":"sha256:$parentId"},"overrides":{"texts":{}}}
    """.trimIndent()

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
    private val baseline: String = baselineWithLanguages("de", "en"),
    private val default: String = minimalEmptyProfileCanonicalJson(id = "default", parentId = "baseline"),
    private val mosa: String = minimalEmptyProfileCanonicalJson(id = "mosa", parentId = "default"),
    private val operator: String = minimalEmptyProfileCanonicalJson(id = "operator", parentId = "mosa"),
) : PodAccess {
    val privacyCalls = mutableListOf<String>()
    val termsCalls = mutableListOf<String>()

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

    override suspend fun privacyClearTerms(): LegalDocumentChainResponse {
        privacyCalls += "baseline"
        privacyCalls += "default"
        privacyCalls += "mosa"
        privacyCalls += "operator"
        return LegalDocumentChainResponse(
            baseline = canonicalJsonEnvelope(baseline),
            profiles = listOf(canonicalJsonEnvelope(default), canonicalJsonEnvelope(mosa), canonicalJsonEnvelope(operator)),
        )
    }

    override suspend fun termsClearTerms(): LegalDocumentChainResponse {
        termsCalls += "baseline"
        termsCalls += "default"
        termsCalls += "mosa"
        termsCalls += "operator"
        return LegalDocumentChainResponse(
            baseline = canonicalJsonEnvelope(baseline),
            profiles = listOf(canonicalJsonEnvelope(default), canonicalJsonEnvelope(mosa), canonicalJsonEnvelope(operator)),
        )
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

    override suspend fun termsBaselineClearTerms(): String {
        termsCalls += "baseline"
        return baseline
    }

    override suspend fun termsDefaultClearTerms(): String {
        termsCalls += "default"
        return default
    }

    override suspend fun termsMosaClearTerms(): String {
        termsCalls += "mosa"
        return mosa
    }

    override suspend fun termsOperatorClearTerms(): String {
        termsCalls += "operator"
        return operator
    }

    override suspend fun termsServiceClearTerms(): String = termsOperatorClearTerms()

    private fun canonicalJsonEnvelope(value: String) =
        buildJsonObject {
            put("canonicalJson", value)
        }
}
