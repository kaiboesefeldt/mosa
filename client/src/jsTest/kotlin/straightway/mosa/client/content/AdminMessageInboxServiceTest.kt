package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
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
import straightway.mosa.content.ContentCapabilitiesResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
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

class AdminMessageInboxServiceTest {
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
    private lateinit var podAccess: RecordingAdminMessagePodAccess
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var logger: RecordingAdminMessageLogger
    private lateinit var timeService: TestTimeService

    @BeforeTest
    fun setUp() {
        podAccess = RecordingAdminMessagePodAccess()
        logger = RecordingAdminMessageLogger()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `loads inbox entries from pod session`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess.messagesResult =
            AdminMessageListResult(
                listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body")),
        )
        val service = AdminMessageInboxService(sessionProvider())

        val result = service.fetchEntries(page = 1, fromTime = 0)

        assertEquals(podAccess.messagesResult.entries, result)
        assertEquals(listOf(1), podAccess.messagesRequests.map { it.page })
    }

    @Test
    fun `loads inbox page metadata from pod session`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess.messagesResult =
            AdminMessageListResult(
                listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body", 6)),
                page = 2,
                hasMore = true,
            )
        val service = AdminMessageInboxService(sessionProvider())

        val result = service.fetch(page = 2, fromTime = 0)

        assertEquals(podAccess.messagesResult, result)
        assertEquals(listOf(2), podAccess.messagesRequests.map { it.page })
    }

    @Test
    fun `forwards fromTime for incremental inbox refresh`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val service = AdminMessageInboxService(sessionProvider())

        service.fetch(page = 1, fromTime = 1234)

        assertEquals(listOf(MessageRequest(jwt = "jwt-token", page = 1, fromTime = 1234)), podAccess.messagesRequests)
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

private class RecordingAdminMessagePodAccess : PodAccess {
    var messagesResult = AdminMessageListResult(emptyList())
    val messagesRequests = mutableListOf<MessageRequest>()

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
    ): AdminMessageListResult {
        messagesRequests += MessageRequest(jwt = jwt, page = page, fromTime = fromTime)
        return messagesResult
    }

    override suspend fun adminMessages(
        recipientGid: String,
        jwt: String,
        page: Int,
    ): AdminMessageListResult = error("not used")

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

private data class MessageRequest(
    val jwt: String,
    val page: Int,
    val fromTime: Long = 0,
)

class StaticAdminMessageIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection = selection()

    override suspend fun movePrevious(): IdentitySelection? = null

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection? = selection()

    override suspend fun import(identity: Identity): IdentitySelection = selection()

    override suspend fun protectCurrent(password: String): IdentitySelection = selection()

    override suspend fun persist() = Unit

    private fun selection(): IdentitySelection = IdentitySelection(identity, index = 0, total = 1, isPersisted = false)
}

class RecordingAdminMessageLogger : Logger {
    override fun warn(
        message: String,
        cause: Throwable?,
    ) = Unit

    override fun error(
        message: String,
        cause: Throwable?,
    ) = Unit
}
