package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.client.identity.Identity
import straightway.mosa.content.ContentCapabilitiesResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.identity.privacy.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult
import straightway.mosa.message.CreateAdminMessageRequest
import kotlin.js.unsafeCast
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminMessageHistoryServiceTest {
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
    private lateinit var podAccess: RecordingAdminHistoryPodAccess
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var logger: RecordingAdminMessageLogger
    private lateinit var timeService: TestTimeService

    @BeforeTest
    fun setUp() {
        podAccess = RecordingAdminHistoryPodAccess()
        logger = RecordingAdminMessageLogger()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `loads admin history page from pod session`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess.adminMessagesResult =
            AdminMessageListResult(
                listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body", 7)),
                page = 2,
                hasMore = true,
            )
        val service = AdminMessageHistoryService(sessionProvider())

        val result = service.fetch("recipient-gid", page = 2)

        assertEquals(podAccess.adminMessagesResult, result)
        assertEquals(listOf(AdminHistoryRequest("recipient-gid", "jwt-token", 2)), podAccess.requests)
    }

    private fun sessionProvider(): PodSessionProvider =
        PodSessionProvider(
            identityRepository = StaticAdminMessageIdentityRepository(identity),
            podBaseProvider = PodBaseProvider(),
            podAccessFactory = { podAccess },
            jwtBuilder = { _, _ -> "jwt-token" },
            certificateCache = certificateCache,
            timeService = timeService,
            logger = logger,
        )
}

private class RecordingAdminHistoryPodAccess : PodAccess {
    var adminMessagesResult = AdminMessageListResult(emptyList())
    val requests = mutableListOf<AdminHistoryRequest>()

    override suspend fun approveGid(gid: String): PodCertificateResponse =
        PodCertificateResponse(
            certificate = PodCertificate(gid, 100_000, "sig", "inf"),
            refreshRecommendedAt = 50_000,
        )

    override suspend fun refreshGid(jwt: String): PodCertificateResponse = error("not used")

    override suspend fun publish(request: PublishRequest, jwt: String) = error("not used")

    override suspend fun reply(request: ReplyRequest, jwt: String) = error("not used")

    override suspend fun deletePosts(jwt: String): DeletePostsResult = error("not used")

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult = error("not used")

    override suspend fun sendAdminMessage(
        request: CreateAdminMessageRequest,
        jwt: String,
    ) = error("not used")

    override suspend fun messages(
        jwt: String,
        page: Int,
        fromTime: Long,
    ): AdminMessageListResult = error("not used")

    override suspend fun adminMessages(
        recipientGid: String,
        jwt: String,
        page: Int,
    ): AdminMessageListResult {
        requests += AdminHistoryRequest(recipientGid, jwt, page)
        return adminMessagesResult
    }

    override suspend fun contentCapabilities(
        jwt: String,
        gid: String?,
    ): ContentCapabilitiesResult = error("not used")

    override suspend fun exportPostsCsv(jwt: String): ByteArray = error("not used")

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray = error("not used")

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult = error("not used")

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult = error("not used")

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult = error("not used")

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult = error("not used")

    override suspend fun privacyBaselineClearTerms(): String = error("not used")

    override suspend fun privacyDefaultClearTerms(): String = error("not used")

    override suspend fun privacyMosaClearTerms(): String = error("not used")

    override suspend fun privacyOperatorClearTerms(): String = error("not used")

    override suspend fun termsBaselineClearTerms(): String = error("not used")

    override suspend fun termsDefaultClearTerms(): String = error("not used")

    override suspend fun termsMosaClearTerms(): String = error("not used")

    override suspend fun termsOperatorClearTerms(): String = error("not used")

    override suspend fun termsServiceClearTerms(): String = error("not used")
}

private data class AdminHistoryRequest(
    val recipientGid: String,
    val jwt: String,
    val page: Int,
)
