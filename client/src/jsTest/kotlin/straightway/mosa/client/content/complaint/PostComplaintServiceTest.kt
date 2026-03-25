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
import straightway.mosa.content.complaint.PostComplaintAvailability
import straightway.mosa.content.complaint.PostComplaintAvailabilityResult
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

class PostComplaintServiceTest {
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
    private lateinit var podAccess: RecordingPostComplaintPodAccess
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var logger: RecordingAdminMessageLogger
    private lateinit var timeService: TestTimeService
    private lateinit var signer: RecordingClientPostComplaintSigner

    @BeforeTest
    fun setUp() {
        podAccess = RecordingPostComplaintPodAccess()
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
    fun `loads post complaint status from pod session`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            podAccess.statusResult = PostComplaintAvailabilityResult("post-1", PostComplaintAvailability.OPEN)
            val service = PostComplaintService(sessionProvider(), signer)

            val result = service.status("post-1")

            assertEquals(PostComplaintAvailabilityResult("post-1", PostComplaintAvailability.OPEN), result)
            assertEquals(listOf("post-1"), podAccess.statusRequests)
        }

    @Test
    fun `submits signed complaint through pod session`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            val service = PostComplaintService(sessionProvider(), signer)

            val success = service.submit("post-1", " Spam ")

            assertTrue(success)
            assertEquals(listOf("post-1" to "Spam"), signer.signComplaintCalls)
            assertEquals(
                listOf(CreatePostComplaintRequest("post-1", "Spam", "signed-complaint", 1)),
                podAccess.createRequests,
            )
        }

    @Test
    fun `rejects blank complaint reason before pod call`() =
        runTest {
            window.asDynamic().MOSA_POD_BASE = "https://pod.example"
            val service = PostComplaintService(sessionProvider(), signer)

            val success = service.submit("post-1", "   ")

            assertFalse(success)
            assertTrue(signer.signComplaintCalls.isEmpty())
            assertTrue(podAccess.createRequests.isEmpty())
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

private class RecordingPostComplaintPodAccess : PodAccess {
    var statusResult = PostComplaintAvailabilityResult("post-1", PostComplaintAvailability.AVAILABLE)
    val statusRequests = mutableListOf<String>()
    val createRequests = mutableListOf<CreatePostComplaintRequest>()

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
    ): PostComplaintAvailabilityResult {
        statusRequests += postId
        return statusResult
    }

    override suspend fun createPostComplaint(
        request: CreatePostComplaintRequest,
        jwt: String,
    ) {
        createRequests += request
    }

    override suspend fun openPostComplaintCount(jwt: String): OpenPostComplaintCountResult = error("not used")

    override suspend fun openPostComplaints(
        jwt: String,
        page: Int,
    ): OpenPostComplaintsResult = error("not used")

    override suspend fun decidePostComplaint(
        request: DecidePostComplaintRequest,
        jwt: String,
    ) = error("not used")

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

class RecordingClientPostComplaintSigner : PostComplaintSigner(
    subtleCrypto = straightway.mosa.client.browser.SubtleCrypto(js("{}").unsafeCast<dynamic>()),
) {
    val signComplaintCalls = mutableListOf<Pair<String, String>>()
    val signDecisionCalls = mutableListOf<List<Any>>()

    override suspend fun signComplaint(
        identity: Identity,
        postId: String,
        reason: String,
    ): CreatePostComplaintRequest {
        signComplaintCalls += postId to reason
        return CreatePostComplaintRequest(postId, reason, "signed-complaint", 1)
    }

    override suspend fun signDecision(
        identity: Identity,
        complaintId: String,
        postId: String,
        targetStatus: PostComplaintState,
        feedback: String,
    ): DecidePostComplaintRequest {
        signDecisionCalls += listOf<Any>(complaintId, postId, targetStatus, feedback)
        return DecidePostComplaintRequest(
            complaintId = complaintId,
            postId = postId,
            targetStatus = targetStatus,
            feedback = feedback,
            signature = "signed-decision",
            signatureVersion = 1,
        )
    }
}
