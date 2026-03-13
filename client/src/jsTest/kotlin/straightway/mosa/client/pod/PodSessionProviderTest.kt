package straightway.mosa.client.pod

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import io.ktor.http.HttpStatusCode
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class PodSessionProviderTest {
    private val timeService = TestTimeService(0)
    private val identity =
        Identity(
            gid = "gid-1",
            keyPair =
                js(
                    """({
                        privateKey: {},
                        publicKey: {}
                    })"""
                ).unsafeCast<CryptoKeyPair>(),
        )

    @BeforeTest
    fun setUp() {
        js("globalThis.MOSA_POD_BASE = 'https://pod.example'")
    }

    @AfterTest
    fun tearDown() {
        js("globalThis.MOSA_POD_BASE = null")
    }

    @Test
    fun `reuses cached certificate without reapproving`() = runTest {
        val podAccess = FakeSessionPodAccess()
        val provider = provider(podAccess)

        val first = provider.execute("publish") { it.jwt }
        val second = provider.execute("publish") { it.jwt }

        assertEquals(1, podAccess.approveCalls)
        assertEquals(listOf("jwt-1", "jwt-1"), listOfNotNull(first, second))
    }

    @Test
    fun `refreshes certificate on unauthorized and retries request`() = runTest {
        val podAccess = FakeSessionPodAccess()
        val provider = provider(podAccess)
        var attempts = 0

        val result =
            provider.execute("publish") { session ->
                attempts++
                if (attempts == 1) throw PodRequestFailed("/v1/publish", PublishRequest("x"), PodSessionProvider.POD_UNAUTHORIZED)
                session.jwt
            }

        assertEquals(2, attempts)
        assertEquals(2, podAccess.approveCalls)
        assertEquals("jwt-2", result)
    }

    @Test
    fun `refreshes certificate when refresh recommendation passed`() = runTest {
        val podAccess = FakeSessionPodAccess()
        val store = InMemoryPodCertificateStore()
        val certificateCache = PodCertificateCache(timeService, store)
        timeService.nowValue = 500
        val cached =
            PodCertificateResponse(
                certificate = PodCertificate(identity.gid, 2000, "sig-cached", null),
                refreshRecommendedAt = 400,
            )
        certificateCache.store(POD_BASE, identity.gid, cached)
        val provider = provider(podAccess, certificateCache)

        val result = provider.execute("publish") { it.jwt }

        assertEquals(1, podAccess.refreshCalls)
        assertEquals(0, podAccess.approveCalls)
        assertEquals("jwt-r1", result)
    }

    @Test
    fun `keeps certificate when refresh returns bad request`() = runTest {
        val podAccess = FakeSessionPodAccess(
            refreshFailure = PodRequestFailed("/v1/refreshgid", Unit, HttpStatusCode.BadRequest),
        )
        val store = InMemoryPodCertificateStore()
        val certificateCache = PodCertificateCache(timeService, store)
        timeService.nowValue = 500
        val cached =
            PodCertificateResponse(
                certificate = PodCertificate(identity.gid, 2000, "sig-cached", null),
                refreshRecommendedAt = 400,
            )
        certificateCache.store(POD_BASE, identity.gid, cached)
        val provider = provider(podAccess, certificateCache)

        val result = provider.execute("publish") { it.jwt }

        assertEquals(1, podAccess.refreshCalls)
        assertEquals(0, podAccess.approveCalls)
        assertEquals("jwt-cached", result)
    }

    @Test
    fun `approves new certificate when refresh is unauthorized`() = runTest {
        val podAccess = FakeSessionPodAccess(
            refreshFailure = PodRequestFailed("/v1/refreshgid", Unit, PodSessionProvider.POD_UNAUTHORIZED),
        )
        val store = InMemoryPodCertificateStore()
        val certificateCache = PodCertificateCache(timeService, store)
        timeService.nowValue = 500
        val cached =
            PodCertificateResponse(
                certificate = PodCertificate(identity.gid, 2000, "sig-cached", null),
                refreshRecommendedAt = 400,
            )
        certificateCache.store(POD_BASE, identity.gid, cached)
        val provider = provider(podAccess, certificateCache)

        val result = provider.execute("publish") { it.jwt }

        assertEquals(1, podAccess.refreshCalls)
        assertEquals(1, podAccess.approveCalls)
        assertEquals("jwt-1", result)
    }

    private fun provider(
        podAccess: FakeSessionPodAccess,
        certificateCache: PodCertificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore()),
    ): PodSessionProvider {
        val baseProvider = PodBaseProvider()
        return PodSessionProvider(
            identityRepository = StaticIdentityRepository(identity),
            podBaseProvider = baseProvider,
            podAccessFactory = { podAccess },
            jwtBuilder = recordingJwtBuilder(),
            certificateCache = certificateCache,
            timeService = timeService,
            logger = RecordingPodSessionLogger(),
        )
    }

    private fun recordingJwtBuilder(): JwtBuilder = { _, certificate ->
        if (certificate.sig.startsWith("sig-r")) {
            "jwt-r${certificate.sig.removePrefix("sig-r")}"
        } else {
            "jwt-${certificate.sig.removePrefix("sig-")}"
        }
    }

    private companion object {
        private const val POD_BASE = "https://pod.example"
    }
}

private class FakeSessionPodAccess(
    private val refreshFailure: PodRequestFailed? = null,
) : PodAccess {
    var approveCalls = 0
    var refreshCalls = 0

    override suspend fun approveGid(gid: String): PodCertificateResponse {
        approveCalls++
        return PodCertificateResponse(
            certificate = PodCertificate(gid = gid, exp = 100_000, sig = "sig-$approveCalls", inf = null),
            refreshRecommendedAt = 50_000,
        )
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        refreshCalls++
        refreshFailure?.let { throw it }
        return PodCertificateResponse(
            certificate = PodCertificate(gid = "gid-1", exp = 20_000, sig = "sig-r$refreshCalls", inf = null),
            refreshRecommendedAt = 10_000,
        )
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("not expected")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("not expected")
    }

    override suspend fun deletePosts(jwt: String): DeletePostsResult {
        error("not expected")
    }

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult {
        error("not expected")
    }

    override suspend fun exportPostsCsv(jwt: String): ByteArray {
        error("not expected")
    }

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray {
        error("not expected")
    }

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult {
        error("not expected")
    }

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult {
        error("not expected")
    }

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult {
        error("not expected")
    }

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult {
        error("not expected")
    }

    override suspend fun privacyBaselineClearTerms(): String {
        error("not expected")
    }

    override suspend fun privacyDefaultClearTerms(): String {
        error("not expected")
    }

    override suspend fun privacyMosaClearTerms(): String {
        error("not expected")
    }

    override suspend fun privacyOperatorClearTerms(): String {
        error("not expected")
    }

    override suspend fun termsBaselineClearTerms(): String {
        error("not expected")
    }

    override suspend fun termsDefaultClearTerms(): String {
        error("not expected")
    }

    override suspend fun termsServiceClearTerms(): String {
        error("not expected")
    }
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1, true)

    override suspend fun movePrevious(): IdentitySelection = selection()

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection = selection()

    override suspend fun import(identity: Identity): IdentitySelection = selection()

    override suspend fun persist() = Unit

    override suspend fun protectCurrent(password: String): IdentitySelection = selection()

    private fun selection(): IdentitySelection = IdentitySelection(identity, 0, 1, true)
}

private class RecordingPodSessionLogger : Logger {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun error(message: String, throwable: Throwable?) {
        errors += message
    }

    override fun warn(message: String, throwable: Throwable?) {
        warnings += message
    }
}
