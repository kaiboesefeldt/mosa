package straightway.mosa.client.content.complaint

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.content.RecordingAdminMessageLogger
import straightway.mosa.client.content.StaticAdminMessageIdentityRepository
import straightway.mosa.client.content.TestTimeService
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.ContentCapabilitiesResult
import straightway.mosa.content.complaint.CreatePostComplaintRequest
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.complaint.PostComplaintAvailabilityResult
import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.OpenPostComplaintCountResult
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.DecidePostComplaintRequest
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.message.AdminMessageListResult
import straightway.mosa.message.CreateAdminMessageRequest
import kotlin.js.unsafeCast
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPostComplaintServiceTest {
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
    private lateinit var podAccess: RecordingAdminPostComplaintPodAccess
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var logger: RecordingAdminMessageLogger
    private lateinit var timeService: TestTimeService
    private lateinit var signer: RecordingClientPostComplaintSigner

    @BeforeTest
    fun setUp() {
        podAccess = RecordingAdminPostComplaintPodAccess()
        logger = RecordingAdminMessageLogger()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
        signer = RecordingClientPostComplaintSigner()
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `loads open complaint count and page from pod session`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            podAccess.countResult = OpenPostComplaintCountResult(2)
            podAccess.listResult =
                OpenPostComplaintsResult(
                    entries =
                        listOf(
                            OpenPostComplaint("complaint-1", "post-1", "Text", "author-1", 1, "complainant-1", "Spam", 2),
                        ),
                    page = 2,
                    hasMore = true,
                )
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            assertEquals(OpenPostComplaintCountResult(2), service.countOpen())
            assertEquals(podAccess.listResult, service.listOpen(page = 2))
            assertEquals(1, podAccess.countRequests.size)
            assertEquals(listOf(2), podAccess.listRequests)
            assertEquals(listOf(false), podAccess.includeProcessedRequests)
        }

    @Test
    fun `reuses cached open complaint count until cache expires`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            podAccess.countResult = OpenPostComplaintCountResult(2)
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            assertEquals(OpenPostComplaintCountResult(2), service.countOpen())
            podAccess.countResult = OpenPostComplaintCountResult(5)

            assertEquals(OpenPostComplaintCountResult(2), service.countOpen())
            assertEquals(1, podAccess.countRequests.size)
        }

    @Test
    fun `reloads open complaint count after cache expires`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            podAccess.countResult = OpenPostComplaintCountResult(2)
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            assertEquals(OpenPostComplaintCountResult(2), service.countOpen())
            timeService.nowValue += 11
            podAccess.countResult = OpenPostComplaintCountResult(5)

            assertEquals(OpenPostComplaintCountResult(5), service.countOpen())
            assertEquals(2, podAccess.countRequests.size)
        }

    @Test
    fun `force refresh bypasses cached open complaint count`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            podAccess.countResult = OpenPostComplaintCountResult(2)
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            assertEquals(OpenPostComplaintCountResult(2), service.countOpen())
            podAccess.countResult = OpenPostComplaintCountResult(5)

            assertEquals(OpenPostComplaintCountResult(5), service.countOpen(forceRefresh = true))
            assertEquals(2, podAccess.countRequests.size)
        }

    @Test
    fun `sends signed decision through pod session`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            val success = service.decide(" complaint-1 ", " post-1 ", PostComplaintState.CONFIRMED, " Checked ")

            assertTrue(success)
            assertEquals(
                listOf(listOf<Any>("complaint-1", "post-1", PostComplaintState.CONFIRMED, "Checked")),
                signer.signDecisionCalls,
            )
            assertEquals(
                listOf(
                    DecidePostComplaintRequest(
                        complaintId = "complaint-1",
                        postId = "post-1",
                        targetStatus = PostComplaintState.CONFIRMED,
                        feedback = "Checked",
                        signature = "signed-decision",
                        signatureVersion = 1,
                    ),
                ),
                podAccess.decisionRequests,
            )
        }

    @Test
    fun `loads processed complaints when requested`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            service.listOpen(page = 1, includeProcessed = true)

            assertEquals(listOf(true), podAccess.includeProcessedRequests)
        }

    @Test
    fun `rejects invalid admin decision before pod call`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            val service = AdminPostComplaintService(sessionProvider(), signer, timeService)

            val success = service.decide("complaint-1", "post-1", PostComplaintState.OPEN, "Feedback")

            assertFalse(success)
            assertTrue(signer.signDecisionCalls.isEmpty())
            assertTrue(podAccess.decisionRequests.isEmpty())
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

private class RecordingAdminPostComplaintPodAccess : PodAccess {
    var countResult = OpenPostComplaintCountResult(0)
    var listResult = OpenPostComplaintsResult(emptyList())
    val countRequests = mutableListOf<String>()
    val listRequests = mutableListOf<Int>()
    val includeProcessedRequests = mutableListOf<Boolean>()
    val decisionRequests = mutableListOf<DecidePostComplaintRequest>()

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
    ): AdminMessageListResult = error("not used")

    override suspend fun contentCapabilities(
        jwt: String,
        gid: String?,
    ): ContentCapabilitiesResult = error("not used")

    override suspend fun postComplaintStatus(
        postId: String,
        jwt: String,
    ): PostComplaintAvailabilityResult = error("not used")

    override suspend fun createPostComplaint(
        request: CreatePostComplaintRequest,
        jwt: String,
    ) = error("not used")

    override suspend fun openPostComplaintCount(jwt: String): OpenPostComplaintCountResult {
        countRequests += jwt
        return countResult
    }

    override suspend fun openPostComplaints(
        jwt: String,
        page: Int,
        includeProcessed: Boolean,
    ): OpenPostComplaintsResult {
        listRequests += page
        includeProcessedRequests += includeProcessed
        return listResult
    }

    override suspend fun decidePostComplaint(
        request: DecidePostComplaintRequest,
        jwt: String,
    ) {
        decisionRequests += request
    }

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
