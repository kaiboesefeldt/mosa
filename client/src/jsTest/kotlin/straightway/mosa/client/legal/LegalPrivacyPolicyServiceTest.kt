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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalPrivacyPolicyServiceTest {
    @Test
    fun `loads baseline and all privacy profiles from pod`() = runTest {
        val podAccess = RecordingPrivacyPodAccess()
        val service =
            LegalPrivacyPolicyService(
                podBaseProvider = fixedPodBaseProvider("https://pod.example"),
                podAccessFactory = { podAccess },
                preferredLanguageTagsProvider = { listOf("de-DE") },
                clearTermsContentProvider = fixedContentProvider(mapOf("de" to "# Datenschutz")),
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
                clearTermsContentProvider = fixedContentProvider(mapOf("en" to "# Privacy")),
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
                clearTermsContentProvider = fixedContentProvider(mapOf("de" to "# Datenschutz")),
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
                        ): Map<String, String> {
                            seenProfiles = profileJsons
                            return mapOf("de" to "# Datenschutz")
                        }
                    },
            )

        val html = service.loadPrivacyPolicyHtml()

        assertContains(html, "<h1>Datenschutz</h1>")
        assertEquals(listOf("default", "mosa"), seenProfiles)
    }
}

private fun fixedPodBaseProvider(base: String): PodBaseProvider =
    PodBaseProvider {
        val globalThis = js("globalThis")
        globalThis.MOSA_POD_BASE = base
        null
    }

private fun fixedContentProvider(textsByLanguage: Map<String, String>): PrivacyClearTermsContentProvider =
    object : PrivacyClearTermsContentProvider {
        override suspend fun renderedTextsByLanguage(
            baselineJson: String,
            profileJsons: List<String>,
        ): Map<String, String> = textsByLanguage
    }

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
