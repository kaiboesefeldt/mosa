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
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult

class PostsExportServiceTest {
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
    private lateinit var podAccess: RecordingExportPodAccess
    private lateinit var logger: RecordingExportLogger
    private lateinit var jwtBuilder: RecordingExportJwtBuilder
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TestTimeService
    private lateinit var downloader: RecordingPostsCsvDownloader

    @BeforeTest
    fun setUp() {
        podAccess = RecordingExportPodAccess()
        logger = RecordingExportLogger()
        jwtBuilder = RecordingExportJwtBuilder()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
        downloader = RecordingPostsCsvDownloader()
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `exports posts csv and triggers downloader`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val service = createService()

        service.export("MosaExport")

        assertEquals(listOf("jwt-gid-123-gid-123"), podAccess.exportInvocations)
        assertEquals(listOf("MosaExport"), downloader.fileBaseNames)
        assertContentEquals("id,text\n1,Hello".encodeToByteArray(), downloader.contents.single())
    }

    @Test
    fun `throws when pod session is unavailable`() = runTest {
        val service = createService()

        assertFailsWith<IllegalStateException> {
            service.export("MosaExport")
        }
    }

    private fun createService(): PostsExportService =
        PostsExportService(
            podSessionProvider =
                PodSessionProvider(
                    identityRepository = ExportStaticIdentityRepository(identity),
                    podBaseProvider = PodBaseProvider(),
                    podAccessFactory = { podAccess },
                    jwtBuilder = { exportIdentity, certificate -> jwtBuilder.build(exportIdentity, certificate) },
                    certificateCache = certificateCache,
                    timeService = timeService,
                    logger = logger,
                ),
            downloader = downloader,
        )
}

private class RecordingExportPodAccess : PodAccess {
    val exportInvocations = mutableListOf<String>()

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
        exportInvocations += jwt
        return "id,text\n1,Hello".encodeToByteArray()
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
}

private class ExportStaticIdentityRepository(
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

private class RecordingExportLogger : Logger {
    override fun error(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

private class RecordingExportJwtBuilder {
    fun build(identity: Identity, certificate: PodCertificate): String =
        "jwt-${identity.gid}-${certificate.gid}"
}

private class RecordingPostsCsvDownloader : PostsCsvDownloader(object : straightway.mosa.client.browser.ObjectUrlFactory {
    override fun create(blob: org.w3c.files.Blob): straightway.mosa.client.browser.ObjectUrlHandle {
        error("not used")
    }
}) {
    val contents = mutableListOf<ByteArray>()
    val fileBaseNames = mutableListOf<String>()

    override suspend fun download(content: ByteArray, fileBaseName: String) {
        contents += content
        fileBaseNames += fileBaseName
    }
}
