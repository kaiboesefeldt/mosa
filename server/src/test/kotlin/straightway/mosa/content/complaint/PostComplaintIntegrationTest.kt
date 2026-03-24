package straightway.mosa.content.complaint

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.lucene.store.ByteBuffersDirectory
import org.junit.jupiter.api.Test
import straightway.mosa.EmbeddingService
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.content.ContentSignatureVerifier
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.StructuredStateDatabase
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.crypto.toBase64
import straightway.mosa.message.AdminMessageController
import straightway.mosa.message.AdminMessageListResult
import straightway.mosa.message.AdminMessageService
import straightway.mosa.message.AdminMessagesTable
import straightway.mosa.message.SqliteAdminMessageStore
import straightway.mosa.testutils.setJsonBody
import straightway.mosa.vectorstore.LuceneIndexAccess
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.vectorstore.LuceneQueryPlanner
import straightway.mosa.vectorstore.QueryOnRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.ArrayDeque
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class PostComplaintIntegrationTest {
    @Test
    fun `user can create complaint and status turns open`() =
        runTest {
            withFixture { fixture ->
                val user = fixture.identity()
                val foreignPostId = fixture.publishPost("author-1", "Foreign post", 1)

                fixture.env {
                    val initial =
                        client.get("/v1/posts/$foreignPostId/complaint-status") {
                            bearerAuth(user.token)
                        }
                    assertEquals(
                        PostComplaintAvailabilityResult(foreignPostId, PostComplaintAvailability.AVAILABLE),
                        initial.body(),
                    )

                    val createResponse =
                        client.post("/v1/post-complaints") {
                            bearerAuth(user.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = foreignPostId,
                                    reason = "Spam",
                                    signature = fixture.signComplaint(user.keyPair, foreignPostId, "Spam"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, createResponse.status)

                    val updated =
                        client.get("/v1/posts/$foreignPostId/complaint-status") {
                            bearerAuth(user.token)
                        }
                    assertEquals(
                        PostComplaintAvailabilityResult(foreignPostId, PostComplaintAvailability.OPEN),
                        updated.body(),
                    )
                }
            }
        }

    @Test
    fun `second open complaint for same post is rejected with conflict`() =
        runTest {
            withFixture { fixture ->
                val user = fixture.identity()
                val secondUser = fixture.identity()
                val foreignPostId = fixture.publishPost("author-1", "Foreign post", 1)

                fixture.env {
                    client.post("/v1/post-complaints") {
                        bearerAuth(user.token)
                        setJsonBody(
                            CreatePostComplaintRequest(
                                postId = foreignPostId,
                                reason = "Spam",
                                signature = fixture.signComplaint(user.keyPair, foreignPostId, "Spam"),
                            ),
                        )
                    }

                    val duplicate =
                        client.post("/v1/post-complaints") {
                            bearerAuth(secondUser.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = foreignPostId,
                                    reason = "Abuse",
                                    signature = fixture.signComplaint(secondUser.keyPair, foreignPostId, "Abuse"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.Conflict, duplicate.status)
                }
            }
        }

    @Test
    fun `own post complaint is rejected with bad request`() =
        runTest {
            withFixture { fixture ->
                val author = fixture.identity()
                val ownPostId = fixture.publishPost(author.gid, "Own post", 1, author.keyPair.public.toBase64())

                fixture.env {
                    val response =
                        client.post("/v1/post-complaints") {
                            bearerAuth(author.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = ownPostId,
                                    reason = "Spam",
                                    signature = fixture.signComplaint(author.keyPair, ownPostId, "Spam"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }
        }

    @Test
    fun `invalid complaint signature is rejected with unprocessable entity`() =
        runTest {
            withFixture { fixture ->
                val user = fixture.identity()
                val foreignPostId = fixture.publishPost("author-1", "Foreign post", 1)

                fixture.env {
                    val response =
                        client.post("/v1/post-complaints") {
                            bearerAuth(user.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = foreignPostId,
                                    reason = "Spam",
                                    signature = fixture.signComplaint(user.keyPair, foreignPostId, "Different"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                }
            }
        }

    @Test
    fun `admin queue shows only open complaints sorted ascending and paged`() =
        runTest {
            withFixture(openPageSize = 1) { fixture ->
                val admin = fixture.identity()
                val user = fixture.identity()
                val secondUser = fixture.identity()
                fixture.adminGids += admin.gid
                val firstPostId = fixture.publishPost("author-1", "First post", 1)
                val secondPostId = fixture.publishPost("author-2", "Second post", 2)
                fixture.createComplaint(user, firstPostId, "Spam")
                fixture.clock.advanceSeconds(1)
                fixture.createComplaint(secondUser, secondPostId, "Abuse")

                fixture.env {
                    val countResponse =
                        client.get("/v1/admin/post-complaints/count") {
                            bearerAuth(admin.token)
                        }
                    assertEquals(OpenPostComplaintCountResult(2), countResponse.body())

                    val firstPage =
                        client.get("/v1/admin/post-complaints") {
                            bearerAuth(admin.token)
                            parameter("page", 1)
                        }
                    assertEquals(
                        OpenPostComplaintsResult(
                            entries =
                                listOf(
                                    OpenPostComplaint(
                                        complaintId = "complaint-1",
                                        postId = firstPostId,
                                        postText = "First post",
                                        postAuthorGid = "author-1",
                                        postCreatedAt = 1,
                                        complainantGid = user.gid,
                                        reason = "Spam",
                                        createdAt = fixture.initialEpochSeconds,
                                    ),
                                ),
                            page = 1,
                            hasMore = true,
                        ),
                        firstPage.body(),
                    )

                    val secondPage =
                        client.get("/v1/admin/post-complaints") {
                            bearerAuth(admin.token)
                            parameter("page", 2)
                        }
                    assertEquals(
                        OpenPostComplaintsResult(
                            entries =
                                listOf(
                                    OpenPostComplaint(
                                        complaintId = "complaint-2",
                                        postId = secondPostId,
                                        postText = "Second post",
                                        postAuthorGid = "author-2",
                                        postCreatedAt = 2,
                                        complainantGid = secondUser.gid,
                                        reason = "Abuse",
                                        createdAt = fixture.initialEpochSeconds + 1,
                                    ),
                                ),
                            page = 2,
                            hasMore = false,
                        ),
                        secondPage.body(),
                    )
                }
            }
        }

    @Test
    fun `non admin cannot access admin complaint endpoints`() =
        runTest {
            withFixture { fixture ->
                val user = fixture.identity()

                fixture.env {
                    val countResponse =
                        client.get("/v1/admin/post-complaints/count") {
                            bearerAuth(user.token)
                        }
                    val listResponse =
                        client.get("/v1/admin/post-complaints") {
                            bearerAuth(user.token)
                        }

                    assertEquals(HttpStatusCode.Forbidden, countResponse.status)
                    assertEquals(HttpStatusCode.Forbidden, listResponse.status)
                }
            }
        }

    @Test
    fun `confirmed complaint allows later new complaint for same post`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.identity()
                val complainant = fixture.identity()
                val nextComplainant = fixture.identity()
                fixture.adminGids += admin.gid
                val postId = fixture.publishPost("author-1", "Foreign post", 1)
                fixture.createComplaint(complainant, postId, "Spam")

                fixture.env {
                    val confirmResponse =
                        client.post("/v1/admin/post-complaints/decision") {
                            bearerAuth(admin.token)
                            setJsonBody(
                                DecidePostComplaintRequest(
                                    complaintId = "complaint-1",
                                    postId = postId,
                                    targetStatus = PostComplaintState.CONFIRMED,
                                    feedback = "Checked",
                                    signature = fixture.signDecision(admin.keyPair, postId, PostComplaintState.CONFIRMED, "Checked"),
                                ),
                            )
                        }
                    assertEquals(HttpStatusCode.NoContent, confirmResponse.status)

                    val nextComplaint =
                        client.post("/v1/post-complaints") {
                            bearerAuth(nextComplainant.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = postId,
                                    reason = "Still bad",
                                    signature = fixture.signComplaint(nextComplainant.keyPair, postId, "Still bad"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, nextComplaint.status)
                }
            }
        }

    @Test
    fun `rejected complaint blocks later new complaint for same post`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.identity()
                val complainant = fixture.identity()
                val nextComplainant = fixture.identity()
                fixture.adminGids += admin.gid
                val postId = fixture.publishPost("author-1", "Foreign post", 1)
                fixture.createComplaint(complainant, postId, "Spam")

                fixture.env {
                    val rejectResponse =
                        client.post("/v1/admin/post-complaints/decision") {
                            bearerAuth(admin.token)
                            setJsonBody(
                                DecidePostComplaintRequest(
                                    complaintId = "complaint-1",
                                    postId = postId,
                                    targetStatus = PostComplaintState.REJECTED,
                                    feedback = "Allowed",
                                    signature = fixture.signDecision(admin.keyPair, postId, PostComplaintState.REJECTED, "Allowed"),
                                ),
                            )
                        }
                    assertEquals(HttpStatusCode.NoContent, rejectResponse.status)

                    val blocked =
                        client.post("/v1/post-complaints") {
                            bearerAuth(nextComplainant.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = postId,
                                    reason = "Still bad",
                                    signature = fixture.signComplaint(nextComplainant.keyPair, postId, "Still bad"),
                                ),
                            )
                        }

                    assertEquals(HttpStatusCode.Conflict, blocked.status)
                }
            }
        }

    @Test
    fun `deleted complaint removes post sends inbox message and blocks later complaint`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.identity()
                val complainant = fixture.identity()
                val nextComplainant = fixture.identity()
                fixture.adminGids += admin.gid
                val postId = fixture.publishPost("author-1", "Foreign post", 1)
                fixture.createComplaint(complainant, postId, "Spam")

                fixture.env {
                    val deleteResponse =
                        client.post("/v1/admin/post-complaints/decision") {
                            bearerAuth(admin.token)
                            setJsonBody(
                                DecidePostComplaintRequest(
                                    complaintId = "complaint-1",
                                    postId = postId,
                                    targetStatus = PostComplaintState.DELETED,
                                    feedback = "Removed",
                                    signature = fixture.signDecision(admin.keyPair, postId, PostComplaintState.DELETED, "Removed"),
                                ),
                            )
                        }
                    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

                    val blocked =
                        client.post("/v1/post-complaints") {
                            bearerAuth(nextComplainant.token)
                            setJsonBody(
                                CreatePostComplaintRequest(
                                    postId = postId,
                                    reason = "Still bad",
                                    signature = fixture.signComplaint(nextComplainant.keyPair, postId, "Still bad"),
                                ),
                            )
                        }
                    assertEquals(HttpStatusCode.NotFound, blocked.status)

                    val inboxResponse =
                        client.get("/v1/messages") {
                            bearerAuth(complainant.token)
                        }
                    assertEquals(
                        AdminMessageListResult(
                            entries =
                                listOf(
                                    straightway.mosa.message.AdminMessage(
                                        "message-1",
                                        admin.gid,
                                        fixture.initialEpochSeconds,
                                        "Complaint update",
                                        "Removed",
                                        28,
                                    ),
                                ),
                            page = 1,
                            hasMore = false,
                        ),
                        inboxResponse.body(),
                    )
                }
            }
        }

    private suspend fun withFixture(
        openPageSize: Int = 10,
        block: suspend (ComplaintIntegrationFixture) -> Unit,
    ) {
        val fixture = ComplaintIntegrationFixture(openPageSize)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private class ComplaintIntegrationFixture(
        openPageSize: Int,
    ) {
        val initialEpochSeconds = 1_773_742_530L
        val clock = MutableClock(Instant.ofEpochSecond(initialEpochSeconds))
        val crypto = CryptoEd25519()
        val signatureVerifierProvider = { Signature.getInstance("Ed25519") }
        val hasherFactory = { MessageDigest.getInstance("SHA-256") }
        val adminGids = linkedSetOf<String>()
        private val directory = Files.createTempDirectory("post-complaint-integration")
        private val dbPath = directory.resolve("mosa_db.sqlite")
        private val database = StructuredStateDatabase(dbPath)
        private val embeddingService = mockk<EmbeddingService>()
        private val vectorStore =
            LuceneLocalVectorStore.create(
                indexAccess = LuceneIndexAccess { ByteBuffersDirectory() },
                paginationRegistry = QueryOnRegistry(pageSize = 10, maxNotFinishedQueries = 10000),
                queryPlanner = LuceneQueryPlanner(maxResults = 1000),
                clock = clock,
                vectorSize = 3,
            )
        val contentService =
            ContentService(
                embeddingService = embeddingService,
                vectorStore = vectorStore,
                hasherFactory = hasherFactory,
                maxFetchItems = 100,
                isAdmin = { gid -> adminGids.contains(gid) },
            )
        private val adminMessageService =
            AdminMessageService(
                store = SqliteAdminMessageStore(database),
                clock = clock,
                isAdmin = { gid -> adminGids.contains(gid) },
                unretrievedTtl = 180.days,
                retrievedTtl = 28.days,
                pageSize = 10,
                idFactory = { "message-1" },
                expectedGidByteLength = hasherFactory().digest(byteArrayOf()).size,
            )
        private val complaintIds = ArrayDeque(listOf("complaint-1", "complaint-2", "complaint-3", "complaint-4"))
        private val postComplaintService =
            PostComplaintService(
                store = SqlitePostComplaintStore(database),
                contentService = contentService,
                adminMessageService = adminMessageService,
                clock = clock,
                isAdmin = { gid -> adminGids.contains(gid) },
                idFactory = { complaintIds.removeFirst() },
                openPageSize = openPageSize,
            )
        val env =
            ControllerHttpTestEnvironment(
                listOf(
                    ContentController(
                        contentService = contentService,
                        contentSignatureVerifier =
                            ContentSignatureVerifier(
                                crypto = crypto,
                                hasherFactory = hasherFactory,
                                signatureVerifierProvider = signatureVerifierProvider,
                            ),
                        clock = clock,
                    ),
                    PostComplaintController(
                        postComplaintService = postComplaintService,
                        postComplaintSignatureVerifier =
                            PostComplaintSignatureVerifier(
                                crypto = crypto,
                                hasherFactory = hasherFactory,
                                signatureVerifierProvider = signatureVerifierProvider,
                            ),
                    ),
                    AdminMessageController(adminMessageService),
                ),
            )

        init {
            every { embeddingService.dimension } returns 3
            coEvery { embeddingService.embed(any()) } returns floatArrayOf(0.1f, 0.2f, 0.3f)
            database.initialize(AdminMessagesTable, PostComplaintsTable)
        }

        fun identity(): TestIdentity {
            val keyPair = crypto.generateKeyPair()
            val gid =
                Base64.UrlSafe
                    .withPadding(Base64.PaddingOption.ABSENT)
                    .encode(
                        hasherFactory().digest(
                            keyPair.public.encoded
                                .takeLast(32)
                                .toByteArray(),
                        ),
                    )
            val certificate = env.podCertService.approveGid(gid, validitySeconds = 3600L)
            return TestIdentity(gid = gid, token = env.jwtBuilder.buildMisJwt(keyPair, certificate), keyPair = keyPair)
        }

        suspend fun publishPost(
            authorGid: String,
            text: String,
            storedAt: Long,
            publicKey: String = "public-key",
        ): String = contentService.publish(authorGid, PublishRequest(text), storedAt, publicKey)

        suspend fun createComplaint(
            identity: TestIdentity,
            postId: String,
            reason: String,
        ) {
            postComplaintService.create(
                complainantGid = identity.gid,
                request =
                    CreatePostComplaintRequest(
                        postId = postId,
                        reason = reason,
                        signature = signComplaint(identity.keyPair, postId, reason),
                    ),
            )
        }

        fun signComplaint(
            keyPair: KeyPair,
            postId: String,
            reason: String,
        ): String = sign(keyPair, PostComplaintSignaturePayload.forComplaint(postId, reason).toCanonicalBytes())

        fun signDecision(
            keyPair: KeyPair,
            postId: String,
            targetStatus: PostComplaintState,
            feedback: String,
        ): String =
            sign(
                keyPair,
                PostComplaintDecisionSignaturePayload.forDecision(postId, targetStatus, feedback).toCanonicalBytes(),
            )

        fun close() {
            vectorStore.close()
            Files.deleteIfExists(dbPath)
            Files.deleteIfExists(directory)
        }

        private fun sign(
            keyPair: KeyPair,
            payload: ByteArray,
        ): String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
                Signature.getInstance("Ed25519").run {
                    initSign(keyPair.private)
                    update(payload)
                    sign()
                },
            )
    }

    private data class TestIdentity(
        val gid: String,
        val token: String,
        val keyPair: KeyPair,
    )

    private class MutableClock(
        private var instant: Instant,
        private val zoneId: ZoneId = ZoneId.of("UTC"),
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        override fun instant(): Instant = instant

        fun advanceSeconds(seconds: Long) {
            instant = instant.plusSeconds(seconds)
        }
    }
}
