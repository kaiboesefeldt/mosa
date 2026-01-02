package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.SearchResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostPublisherTest {
    private val identity =
        Identity(
            gid = "gid-123",
            keyPair =
                js(
                    """({
                        privateKey: {},
                        publicKey: {}
                    })"""
                ).unsafeCast<CryptoKeyPair>(),
        )
    private lateinit var podAccess: RecordingPublishPodAccess
    private lateinit var logger: PublishRecordingLogger
    private lateinit var jwtBuilder: RecordingJwtBuilder
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TestTimeService

    @BeforeTest
    fun setUp() {
        podAccess = RecordingPublishPodAccess()
        logger = PublishRecordingLogger()
        jwtBuilder = RecordingJwtBuilder()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService)
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `publishes trimmed text when base and identity present`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val publisher = createPublisher()

        val success = publisher.publish("  Hallo Welt  ")

        assertTrue(success)
        assertEquals(listOf(identity.gid), podAccess.approveInvocations)
        assertEquals(listOf(PublishRequest("Hallo Welt")), podAccess.publishRequests)
        val jwtInvocation = jwtBuilder.invocations.single()
        assertEquals(identity.gid, jwtInvocation.first.gid)
        assertEquals(identity.gid, jwtInvocation.second.gid)
    }

    @Test
    fun `returns false when pod base missing`() = runTest {
        val publisher = createPublisher()

        val success = publisher.publish("Fehlender Pod")

        assertFalse(success)
        assertTrue(podAccess.approveInvocations.isEmpty())
        assertTrue(podAccess.publishRequests.isEmpty())
    }

    private fun createPublisher(): PostPublisher =
        PostPublisher(
            PodSessionProvider(
                identityRepository = PublishStaticIdentityRepository(identity),
                podBaseProvider = PodBaseProvider(),
                podAccessFactory = { podAccess },
                jwtBuilder = { identity, certificate -> jwtBuilder.build(identity, certificate) },
                certificateCache = certificateCache,
                timeService = timeService,
                logger = logger,
            )
        )
}

private class RecordingPublishPodAccess : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val publishRequests = mutableListOf<PublishRequest>()

    override suspend fun approveGid(gid: String): PodCertificate {
        approveInvocations += gid
        return PodCertificate(gid, 100_000, "sig", "inf")
    }

    override suspend fun refreshGid(jwt: String): PodCertificate {
        error("not used")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        publishRequests += request
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
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
}

private class PublishStaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1, true)

    override suspend fun movePrevious(): IdentitySelection = selection()

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection = selection()

    override suspend fun import(identity: Identity): IdentitySelection = selection()

    override suspend fun protectCurrent(password: String): IdentitySelection = selection()

    override suspend fun persist() = Unit

    private fun selection(): IdentitySelection = IdentitySelection(identity, 0, 1, true)
}

private class PublishRecordingLogger : Logger {
    val errors = mutableListOf<String>()

    override fun error(message: String, throwable: Throwable?) {
        errors += message
    }

    override fun warn(message: String, throwable: Throwable?) {}
}

private class RecordingJwtBuilder {
    val invocations = mutableListOf<Pair<Identity, PodCertificate>>()

    fun build(identity: Identity, certificate: PodCertificate): String {
        invocations += identity to certificate
        return "jwt-token"
    }
}
