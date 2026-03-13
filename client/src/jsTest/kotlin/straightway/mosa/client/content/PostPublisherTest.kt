package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.unsafeCast
import kotlin.js.Promise
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.ContentSignaturePayload
import straightway.mosa.content.ContentSignatureVersion
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
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
    private lateinit var subtleCrypto: RecordingPublishSubtleCrypto
    private lateinit var timeService: TestTimeService

    @BeforeTest
    fun setUp() {
        podAccess = RecordingPublishPodAccess()
        logger = PublishRecordingLogger()
        jwtBuilder = RecordingJwtBuilder()
        subtleCrypto = RecordingPublishSubtleCrypto()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
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
        val canonicalPayload = ContentSignaturePayload.forPost("Hallo Welt").toCanonicalBytes()
        assertEquals(
            listOf(
                PublishRequest(
                    text = "Hallo Welt",
                    signature = canonicalPayload.toBase64Url(),
                    signatureVersion = ContentSignatureVersion.V1,
                ),
            ),
            podAccess.publishRequests,
        )
        val signCall = subtleCrypto.signCalls.single()
        assertEquals("Ed25519", signCall.algorithm)
        assertEquals(canonicalPayload.toList(), signCall.bytes.toList())
        assertEquals(identity.keyPair.privateKey, signCall.privateKey)
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
            podSessionProvider =
                PodSessionProvider(
                    identityRepository = PublishStaticIdentityRepository(identity),
                    podBaseProvider = PodBaseProvider(),
                    podAccessFactory = { podAccess },
                    jwtBuilder = { identity, certificate -> jwtBuilder.build(identity, certificate) },
                    certificateCache = certificateCache,
                    timeService = timeService,
                    logger = logger,
                ),
            contentSigner = ContentSigner(SubtleCrypto(subtleCrypto.wrapped)),
        )
}

private class RecordingPublishSubtleCrypto {
    data class SignCall(
        val algorithm: String,
        val privateKey: CryptoKey,
        val bytes: ByteArray,
    )

    val signCalls = mutableListOf<SignCall>()
    val wrapped =
        run {
            val subtle = js("{}").unsafeCast<dynamic>()
            subtle.sign = { algorithm: String, privateKey: CryptoKey, bytes: ByteArray ->
                signCalls += SignCall(algorithm, privateKey, bytes)
                Promise.resolve(bytes.toArrayBuffer())
            }
            subtle
        }

    private fun ByteArray.toArrayBuffer(): ArrayBuffer =
        ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }
}

private class RecordingPublishPodAccess : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val publishRequests = mutableListOf<PublishRequest>()

    override suspend fun approveGid(gid: String): PodCertificateResponse {
        approveInvocations += gid
        return PodCertificateResponse(
            certificate = PodCertificate(gid, 100_000, "sig", "inf"),
            refreshRecommendedAt = 50_000,
        )
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        error("not used")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        publishRequests += request
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
        error("not used")
    }

    override suspend fun privacyDefaultClearTerms(): String {
        error("not used")
    }

    override suspend fun privacyMosaClearTerms(): String {
        error("not used")
    }

    override suspend fun privacyOperatorClearTerms(): String {
        error("not used")
    }

    override suspend fun termsBaselineClearTerms(): String {
        error("not used")
    }

    override suspend fun termsDefaultClearTerms(): String {
        error("not used")
    }

    override suspend fun termsServiceClearTerms(): String {
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
