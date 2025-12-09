package straightway.mosa.client.pod

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.TestTimeService
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    private fun provider(podAccess: FakeSessionPodAccess): PodSessionProvider {
        val baseProvider = PodBaseProvider()
        val certificateCache = PodCertificateCache(timeService)
        return PodSessionProvider(
            identityRepository = StaticIdentityRepository(identity),
            podBaseProvider = baseProvider,
            podAccessFactory = { podAccess },
            jwtBuilder = recordingJwtBuilder(),
            certificateCache = certificateCache,
            logger = RecordingPodSessionLogger(),
        )
    }

    private fun recordingJwtBuilder(): JwtBuilder = { _, certificate ->
        "jwt-${certificate.sig.removePrefix("sig-")}"
    }
}

private class FakeSessionPodAccess : PodAccess {
    var approveCalls = 0

    override suspend fun approveGid(gid: String): PodCertificate {
        approveCalls++
        return PodCertificate(gid = gid, exp = 10_000, sig = "sig-$approveCalls", inf = null)
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("not expected")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
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
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1)

    override suspend fun movePrevious(): IdentitySelection = selection()

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection = selection()

    override suspend fun import(identity: Identity): IdentitySelection = selection()

    private fun selection(): IdentitySelection = IdentitySelection(identity, 0, 1)
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
