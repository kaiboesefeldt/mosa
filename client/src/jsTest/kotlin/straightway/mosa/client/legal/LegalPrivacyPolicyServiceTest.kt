package straightway.mosa.client.legal

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
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
    fun `drops duplicate fallback profile before clearterms rendering`() = runTest {
        val podAccess = RecordingPrivacyPodAccess(operator = "mosa")
        var seenProfiles: List<String>? = null
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
                            seenProfiles = profileJsons
                            return renderResultOf(mapOf("de" to "# Datenschutz"))
                        }
                    },
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
        assertEquals(listOf("default", "mosa"), seenProfiles)
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
            """Operator: <span class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">Org A</span>.""",
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
            """<div class="legal-ct-bg-red" style="background-color: #fee2e2; color: #7f1d1d;"><p><span class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">Block text</span></p></div>""",
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
            """<span class="legal-ct-bg-red legal-ct-baseline-diff" style="background-color: #fee2e2; color: #7f1d1d;">A</span><span class="legal-ct-bg-yellow legal-ct-baseline-diff" style="background-color: #fef3c7; color: #78350f;">B</span>""",
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

        assertContains(html, """<div class="legal-ct-baseline-diff"><p>Changed text</p></div>""")
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
