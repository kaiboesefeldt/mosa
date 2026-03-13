package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.js
import kotlin.js.unsafeCast
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.client.pod.TestTimeService
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult

class PrivacySnapshotServiceTest {
    private val identity =
        Identity(
            gid = "gid-123",
            keyPair =
                js(
                    """({
                        privateKey: {},
                        publicKey: {}
                    })""",
                ).unsafeCast<CryptoKeyPair>(),
        )
    private lateinit var podAccess: RecordingPrivacySnapshotPodAccess
    private lateinit var logger: RecordingPrivacySnapshotLogger
    private lateinit var jwtBuilder: RecordingPrivacySnapshotJwtBuilder
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TestTimeService
    private lateinit var transfer: RecordingPrivacySnapshotTransferPort

    @BeforeTest
    fun setUp() {
        podAccess = RecordingPrivacySnapshotPodAccess()
        logger = RecordingPrivacySnapshotLogger()
        jwtBuilder = RecordingPrivacySnapshotJwtBuilder()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
        transfer = RecordingPrivacySnapshotTransferPort()
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `freeze downloads snapshot from pod`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val service = createService()

        service.freeze("MosaSnapshot")

        assertEquals(listOf("jwt-gid-123-gid-123"), podAccess.freezeInvocations)
        assertEquals(listOf("MosaSnapshot"), transfer.downloadedFileBaseNames)
        assertContentEquals(byteArrayOf(1, 2, 3), transfer.downloadedSnapshots.single())
    }

    @Test
    fun `freeze throws when pod session is unavailable`() = runTest {
        val service = createService()

        assertFailsWith<IllegalStateException> {
            service.freeze("MosaSnapshot")
        }
    }

    @Test
    fun `restore uploads snapshot and returns summary`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        transfer.uploadedSnapshot = byteArrayOf(4, 5, 6)
        val service = createService()

        val result = service.restore()

        assertEquals(FreezeSnapshotRestoreResult(listOf("post-1"), 1), result)
        assertEquals(listOf("jwt-gid-123-gid-123"), podAccess.restoreJwtInvocations)
        assertContentEquals(byteArrayOf(4, 5, 6), podAccess.restoreSnapshots.single())
    }

    @Test
    fun `restore returns null when upload is cancelled`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val service = createService()

        val result = service.restore()

        assertNull(result)
        assertEquals(emptyList(), podAccess.restoreJwtInvocations)
    }

    private fun createService(): PrivacySnapshotService =
        PrivacySnapshotService(
            podSessionProvider =
                PodSessionProvider(
                    identityRepository = PrivacySnapshotStaticIdentityRepository(identity),
                    podBaseProvider = PodBaseProvider(),
                    podAccessFactory = { podAccess },
                    jwtBuilder = { exportIdentity, certificate -> jwtBuilder.build(exportIdentity, certificate) },
                    certificateCache = certificateCache,
                    timeService = timeService,
                    logger = logger,
                ),
            transfer = transfer,
        )
}

private class RecordingPrivacySnapshotPodAccess : PodAccess {
    val freezeInvocations = mutableListOf<String>()
    val restoreJwtInvocations = mutableListOf<String>()
    val restoreSnapshots = mutableListOf<ByteArray>()

    override suspend fun approveGid(gid: String): PodCertificateResponse =
        PodCertificateResponse(
            certificate = PodCertificate(gid, 100_000, "sig", "inf"),
            refreshRecommendedAt = 50_000,
        )

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
        freezeInvocations += jwt
        return byteArrayOf(1, 2, 3)
    }

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult {
        restoreJwtInvocations += jwt
        restoreSnapshots += snapshot
        return FreezeSnapshotRestoreResult(listOf("post-1"), 1)
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

    override suspend fun termsMosaClearTerms(): String {
        error("not used")
    }

    override suspend fun termsOperatorClearTerms(): String {
        error("not used")
    }

    override suspend fun termsServiceClearTerms(): String {
        error("not used")
    }
}

private class PrivacySnapshotStaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection = IdentitySelection(identity, 0, 1, true)

    override suspend fun movePrevious(): IdentitySelection = loadOrCreate()

    override suspend fun moveNext(): IdentitySelection = loadOrCreate()

    override suspend fun deleteCurrent(): IdentitySelection = loadOrCreate()

    override suspend fun select(index: Int): IdentitySelection = loadOrCreate()

    override suspend fun import(identity: Identity): IdentitySelection = loadOrCreate()

    override suspend fun protectCurrent(password: String): IdentitySelection = loadOrCreate()

    override suspend fun persist() = Unit
}

private class RecordingPrivacySnapshotLogger : Logger {
    override fun error(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

private class RecordingPrivacySnapshotJwtBuilder {
    fun build(identity: Identity, certificate: PodCertificate): String =
        "jwt-${identity.gid}-${certificate.gid}"
}

private class RecordingPrivacySnapshotTransferPort : PrivacySnapshotTransferPort {
    val downloadedSnapshots = mutableListOf<ByteArray>()
    val downloadedFileBaseNames = mutableListOf<String>()
    var uploadedSnapshot: ByteArray? = null

    override suspend fun download(
        snapshot: ByteArray,
        fileBaseName: String,
    ) {
        downloadedSnapshots += snapshot
        downloadedFileBaseNames += fileBaseName
    }

    override suspend fun upload(): ByteArray? = uploadedSnapshot
}
