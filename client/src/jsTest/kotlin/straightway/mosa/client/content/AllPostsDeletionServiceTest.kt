package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.js
import kotlin.js.unsafeCast
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult

class AllPostsDeletionServiceTest {
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
    private lateinit var podAccess: RecordingDeletePodAccess
    private lateinit var logger: RecordingDeleteLogger
    private lateinit var jwtBuilder: RecordingDeleteJwtBuilder
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TestTimeService

    @BeforeTest
    fun setUp() {
        podAccess = RecordingDeletePodAccess()
        logger = RecordingDeleteLogger()
        jwtBuilder = RecordingDeleteJwtBuilder()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `deletes all posts and evicts caches`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val postCache = DeleteRecordingPostCache()
        val replyCache = DeleteRecordingReplyCache()
        val service = createService(postCache, replyCache)

        val deleted = service.deleteAll()

        assertEquals(listOf("id-a", "id-b"), deleted)
        assertEquals(listOf("id-a", "id-b"), postCache.evictedIds)
        assertEquals(listOf("id-a", "id-b"), replyCache.evictedIds)
        assertEquals(listOf(identity.gid), podAccess.approveInvocations)
    }

    @Test
    fun `returns null when pod base missing`() = runTest {
        val postCache = DeleteRecordingPostCache()
        val replyCache = DeleteRecordingReplyCache()
        val service = createService(postCache, replyCache)

        val deleted = service.deleteAll()

        assertNull(deleted)
        assertEquals(emptyList(), postCache.evictedIds)
        assertEquals(emptyList(), replyCache.evictedIds)
    }

    private fun createService(
        postCache: DeleteRecordingPostCache,
        replyCache: DeleteRecordingReplyCache,
    ): AllPostsDeletionService =
        AllPostsDeletionService(
            PodSessionProvider(
                identityRepository = DeleteStaticIdentityRepository(identity),
                podBaseProvider = PodBaseProvider(),
                podAccessFactory = { podAccess },
                jwtBuilder = { identity, certificate -> jwtBuilder.build(identity, certificate) },
                certificateCache = certificateCache,
                timeService = timeService,
                logger = logger,
            ),
            postCache,
            replyCache,
        )
}

private class RecordingDeletePodAccess : PodAccess {
    val approveInvocations = mutableListOf<String>()

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
        error("not used")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("not used")
    }

    override suspend fun deletePosts(jwt: String): DeletePostsResult =
        DeletePostsResult(listOf("id-a", "id-b"))

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
}

private class DeleteStaticIdentityRepository(
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

private class RecordingDeleteLogger : Logger {
    override fun error(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

private class RecordingDeleteJwtBuilder {
    fun build(identity: Identity, certificate: PodCertificate): String =
        "jwt-${identity.gid}-${certificate.gid}"
}

private class DeleteRecordingPostCache : PostCache {
    val evictedIds = mutableListOf<String>()

    override suspend fun snapshot(topic: String): PostCacheSnapshot = PostCacheSnapshot(emptyList(), null)

    override suspend fun append(topic: String, entries: List<TopicScopedPost>): PostCacheSnapshot =
        PostCacheSnapshot(emptyList(), null)

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        CachedPostResolution(emptyMap(), ids.toList())

    override suspend fun evict(postId: String) {
        evictedIds += postId
    }

    override suspend fun clear(topic: String) = Unit
}

private class DeleteRecordingReplyCache : ReplyCache {
    val evictedIds = mutableListOf<String>()

    override suspend fun snapshot(rootId: String): ReplyCacheSnapshot = ReplyCacheSnapshot(emptyList(), null)

    override suspend fun append(rootId: String, entries: List<ThreadScopedReply>): ReplyCacheSnapshot =
        ReplyCacheSnapshot(emptyList(), null)

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        CachedPostResolution(emptyMap(), ids.toList())

    override suspend fun evict(postId: String) {
        evictedIds += postId
    }

    override suspend fun clear(rootId: String) = Unit
}
