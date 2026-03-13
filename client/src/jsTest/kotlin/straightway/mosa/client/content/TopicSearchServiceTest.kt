package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
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
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicSearchServiceTest {
    private lateinit var podAccess: FakePodAccess
    private lateinit var logger: RecordingLogger
    private lateinit var cache: FakePostCache
    private lateinit var subtleCrypto: FakeVerifierSubtleCrypto
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TestTimeService
    private val jwtInvocations = mutableListOf<Pair<Identity, PodCertificate>>()
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

    @BeforeTest
    fun setup() {
        logger = RecordingLogger()
        cache = FakePostCache()
        subtleCrypto = FakeVerifierSubtleCrypto()
        timeService = TestTimeService(0)
        certificateCache = PodCertificateCache(timeService, InMemoryPodCertificateStore())
        clearBase()
        jwtInvocations.clear()
    }

    @AfterTest
    fun tearDown() {
        clearBase()
    }

    @Test
    fun `returns empty when pod base missing`() = runTest {
        podAccess = FakePodAccess()
        val service = createService()

        val results = service.fetchFor(listOf(Topic("Any")))

        assertTrue(results.isEmpty())
        assertTrue(podAccess.approveInvocations.isEmpty())
    }

    @Test
    fun `searches topics and fetches missing posts once`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            FakePodAccess(
                searchResponses =
                    mapOf(
                        "Alpha" to listOf(searchResult("p1", "q-alpha")),
                        "Beta" to
                            listOf(
                                searchResult(
                                    listOf(
                                        SearchResult.Entry("p1", 0.8f),
                                        SearchResult.Entry("p2", 0.7f),
                                    ),
                                    "q-beta",
                                ),
                            ),
                    ),
                fetchableEntries =
                    mapOf(
                        "p1" to fetchEntry("p1", "content-1"),
                        "p2" to fetchEntry("p2", "content-2"),
                    ),
            )
        val service = createService()
        val topics = listOf(Topic("Alpha"), Topic("Beta"))

        val results = service.fetchFor(topics)

        assertEquals(1, podAccess.approveInvocations.size)
        assertEquals(identity.gid, podAccess.approveInvocations.single())
        assertEquals(3, jwtInvocations.size)
        val tokenInvocation = jwtInvocations.first()
        assertEquals(identity.gid, tokenInvocation.first.gid)
        assertEquals(identity.gid, tokenInvocation.second.gid)
        assertEquals(1, jwtInvocations.map { it.second.gid }.distinct().size)
        assertEquals(2, podAccess.searchInvocations.size)
        assertEquals(listOf("Alpha", "Beta"), podAccess.searchInvocations.map { it.topic })
        assertEquals(1, podAccess.fetchInvocations.size)
        assertEquals(setOf("p1", "p2"), podAccess.fetchInvocations.single().itemIds.toSet())
        assertEquals("p1", results.first().entries.first().id)
        assertEquals(listOf("p1", "p2"), results.last().entries.map { it.id })
    }

    @Test
    fun `skips topic when search fails`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            FakePodAccess(
                searchResponses = mapOf("Alpha" to listOf(searchResult("ok", "q1"))),
                errorForTopic = "Beta",
                fetchableEntries = mapOf("ok" to fetchEntry("ok", "text")),
            )
        val service = createService()
        val topics = listOf(Topic("Alpha"), Topic("Beta"))

        val results = service.fetchFor(topics)

        assertEquals(1, results.size)
        assertTrue(logger.warnMessages.any { it.contains("Beta") })
    }

    @Test
    fun `discards invalid fetched posts and keeps valid results usable`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            FakePodAccess(
                searchResponses =
                    mapOf(
                        "Alpha" to
                            listOf(
                                searchResult(
                                    listOf(
                                        SearchResult.Entry("valid", 0.8f),
                                        SearchResult.Entry("invalid", 0.7f),
                                    ),
                                    "query-alpha",
                                ),
                            ),
                    ),
                fetchableEntries =
                    mapOf(
                        "valid" to subtleCrypto.validFetchEntry(id = "valid", content = "good", storedAt = 20),
                        "invalid" to
                            subtleCrypto.validFetchEntry(id = "invalid", content = "bad", storedAt = 10).copy(
                                authorSignature = "tampered",
                            ),
                    ),
            )
        val service = createService()

        val results = service.fetchFor(listOf(Topic("Alpha")))

        assertEquals(listOf("valid"), results.single().entries.map { it.id })
        assertEquals(listOf("valid"), cache.snapshot("Alpha").entries.map { it.post.id })
        assertTrue(logger.warnMessages.any { it.contains("invalid") && it.contains("Discarded") })
    }

    @Test
    fun `reuses cached posts without fetching`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val cachedSnapshot =
            PostCacheSnapshot(
                entries = listOf(cachedPost("cached", "cached-content", 0.7f, storedAt = 200)),
                lastFetchedAt = 200,
            )
        cache.prime("Alpha", cachedSnapshot)
        podAccess =
            FakePodAccess(
                searchResponses = mapOf("Alpha" to listOf(searchResult("cached", "query-on"))),
            )
        val service = createService()

        val results = service.fetchFor(listOf(Topic("Alpha")))

        assertTrue(podAccess.fetchInvocations.isEmpty())
        assertEquals(listOf("cached"), results.single().entries.map { it.id })
    }

    @Test
    fun `uses score filter and cached timestamp for search`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val cachedSnapshot =
            PostCacheSnapshot(
                entries = listOf(cachedPost("cached", "cached-content", 0.7f, storedAt = 1234)),
                lastFetchedAt = 1234,
            )
        cache.prime("Alpha", cachedSnapshot)
        podAccess =
            FakePodAccess(
                searchResponses = mapOf("Alpha" to listOf(searchResult("cached", "query-on"))),
                fetchableEntries = mapOf("cached" to fetchEntry("cached", "cached-content")),
            )
        val service = createService()

        service.fetchFor(listOf(Topic("Alpha", scoreMustBeAbove = 0.9f)))

        val invocation = podAccess.searchInvocations.single()
        assertEquals(0.9f, invocation.scoreMustBeAbove)
        assertEquals(1234, invocation.fromTime)
    }

    @Test
    fun `keeps cached posts and resumes below cleared filter`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val cachedSnapshot =
            PostCacheSnapshot(
                entries = listOf(cachedPost("cached", "cached-content", 0.9f, storedAt = 200)),
                lastFetchedAt = 200,
            )
        cache.prime("Alpha", cachedSnapshot)
        podAccess =
            FakePodAccess(
                searchResponses =
                    mapOf(
                        "Alpha" to
                            listOf(
                                SearchResult(emptyList(), ""),
                                searchResult("new", "", 0.7f),
                            ),
                    ),
                fetchableEntries = mapOf("new" to fetchEntry("new", "content-new", storedAt = 150)),
            )
        val service = createService()

        val previousFilter = 0.8f
        service.fetchFor(listOf(Topic("Alpha", scoreMustBeAbove = previousFilter)))
        service.filterCleared(Topic("Alpha", scoreMustBeAbove = previousFilter), previousFilter)
        service.fetchFor(listOf(Topic("Alpha")))

        assertEquals(listOf(0.8f, null), podAccess.searchInvocations.map { it.scoreMustBeAbove })
        assertEquals(listOf(null, 0.8f), podAccess.searchInvocations.map { it.scoreMustBeBelow })
        assertEquals(listOf(200L, null), podAccess.searchInvocations.map { it.fromTime })
        val snapshot = cache.snapshot("Alpha")
        assertEquals(listOf("cached", "new"), snapshot.entries.map { it.post.id })
    }

    @Test
    fun `keeps filter relaxation when session unavailable`() = runTest {
        val previousFilter = 0.8f
        val cachedSnapshot =
            PostCacheSnapshot(
                entries = listOf(cachedPost("cached", "cached-content", 0.9f, storedAt = 200)),
                lastFetchedAt = 200,
            )
        cache.prime("Alpha", cachedSnapshot)
        podAccess =
            FakePodAccess(
                searchResponses = mapOf("Alpha" to listOf(searchResult("new", "query-on", 0.7f))),
                fetchableEntries = mapOf("new" to fetchEntry("new", "content-new")),
            )
        val service = createService()

        service.filterCleared(Topic("Alpha", scoreMustBeAbove = previousFilter), previousFilter)
        val missingSessionResult = service.fetchFor(listOf(Topic("Alpha")))

        assertTrue(missingSessionResult.isNotEmpty())
        assertTrue(podAccess.searchInvocations.isEmpty())

        window.asDynamic().MOSA_POD_BASE = "https://pod.example"

        val results = service.fetchFor(listOf(Topic("Alpha")))

        assertEquals(1, results.size)
        val invocation = podAccess.searchInvocations.single()
        assertEquals(previousFilter, invocation.scoreMustBeBelow)
        assertNull(invocation.fromTime)
    }

    @Test
    fun `paginates with descending score filter until exhausted`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            FakePodAccess(
                searchResponses =
                    mapOf(
                        "Alpha" to
                            listOf(
                                searchResult(
                                    listOf(
                                        SearchResult.Entry("p1", 0.9f),
                                        SearchResult.Entry("p2", 0.8f),
                                    ),
                                    "page-1",
                                ),
                                searchResult("p3", "page-2", 0.7f),
                                SearchResult(emptyList(), ""),
                            ),
                    ),
                fetchableEntries =
                    mapOf(
                        "p1" to fetchEntry("p1", "content-1"),
                        "p2" to fetchEntry("p2", "content-2"),
                        "p3" to fetchEntry("p3", "content-3"),
                    ),
            )
        val service = createService()

        val initial = service.fetchFor(listOf(Topic("Alpha"))).single()
        val next = service.fetchMore(Topic("Alpha"))
        val exhausted = service.fetchMore(Topic("Alpha"))

        assertEquals(listOf("p1", "p2"), initial.entries.map { it.id })
        assertEquals(listOf("p1", "p2", "p3"), next?.entries?.map { it.id })
        assertNull(exhausted)
        assertEquals(listOf(null, 0.8f, 0.7f), podAccess.searchInvocations.map { it.scoreMustBeBelow })
    }

    private fun createService(): TopicSearchService =
        TopicSearchService(
            podSessionProvider =
                PodSessionProvider(
                    identityRepository = StaticIdentityRepository(identity),
                    podBaseProvider = PodBaseProvider(),
                    podAccessFactory = { podAccess },
                    jwtBuilder = { identity, cert ->
                        jwtInvocations += identity to cert
                        "generated-jwt"
                    },
                    certificateCache = certificateCache,
                    timeService = timeService,
                    logger = logger,
                ),
            postCache = cache,
            contentSignatureVerifier = ContentSignatureVerifier(subtleCrypto.subtleCrypto),
            logger = logger,
        )

    private fun fetchEntry(
        id: String,
        content: String,
        storedAt: Long = 0,
    ): FetchResult.Entry = subtleCrypto.validFetchEntry(id = id, content = content, storedAt = storedAt)

    private fun clearBase() {
        window.asDynamic().MOSA_POD_BASE = null
    }
}

private class FakePodAccess(
    val searchResponses: Map<String, List<SearchResult>> = emptyMap(),
    private val fetchableEntries: Map<String, FetchResult.Entry> = emptyMap(),
    private val errorForTopic: String? = null,
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val searchInvocations = mutableListOf<QueryRequest>()
    val fetchInvocations = mutableListOf<FetchRequest>()
    private val searchQueues = searchResponses.mapValues { (_, value) -> value.toMutableList() }

    override suspend fun approveGid(gid: String): PodCertificateResponse {
        approveInvocations += gid
        return PodCertificateResponse(
            certificate = PodCertificate(gid, 100_000, "sig", "inf"),
            refreshRecommendedAt = 50_000,
        )
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        error("Not implemented")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun deletePosts(jwt: String): DeletePostsResult {
        error("Not implemented")
    }

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult {
        error("Not implemented")
    }

    override suspend fun exportPostsCsv(jwt: String): ByteArray {
        error("Not implemented")
    }

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray {
        error("Not implemented")
    }

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult {
        error("Not implemented")
    }

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult {
        searchInvocations += request
        val topic = request.topic ?: error("topic missing")
        if (topic == errorForTopic) throw RuntimeException("boom")
        val queue = searchQueues[topic] ?: error("no response for $topic")
        return queue.removeFirstOrNull() ?: error("no response for $topic")
    }

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult {
        error("searchOn not expected")
    }

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult {
        fetchInvocations += request
        val entries =
            request.itemIds.map { id ->
                fetchableEntries[id] ?: error("no entry for $id")
            }
        return FetchResult(entries)
    }

    override suspend fun privacyBaselineClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun privacyDefaultClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun privacyMosaClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun privacyOperatorClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsBaselineClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsDefaultClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsServiceClearTerms(): String {
        error("Not implemented")
    }
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1, true)

    override suspend fun movePrevious(): IdentitySelection = identitySelection()

    override suspend fun moveNext(): IdentitySelection = identitySelection()

    override suspend fun deleteCurrent(): IdentitySelection = identitySelection()

    override suspend fun select(index: Int): IdentitySelection = identitySelection()

    override suspend fun import(identity: Identity): IdentitySelection = identitySelection()

    override suspend fun protectCurrent(password: String): IdentitySelection = identitySelection()

    override suspend fun persist() = Unit

    private fun identitySelection(): IdentitySelection = IdentitySelection(identity, 0, 1, true)
}

private class RecordingLogger : Logger {
    val warnMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()

    override fun error(message: String, throwable: Throwable?) {
        errorMessages += message
    }

    override fun warn(message: String, throwable: Throwable?) {
        warnMessages += message
    }
}

private class FakePostCache : PostCache {
    val snapshots = mutableMapOf<String, PostCacheSnapshot>()
    private val storedPosts = mutableMapOf<String, CachedPost>()

    override suspend fun snapshot(topic: String): PostCacheSnapshot =
        snapshots[topic] ?: PostCacheSnapshot(emptyList(), null)

    override suspend fun append(topic: String, entries: List<TopicScopedPost>): PostCacheSnapshot {
        if (entries.isEmpty()) {
            return snapshots[topic] ?: PostCacheSnapshot(emptyList(), null)
        }
        val current = snapshots[topic]
        entries.forEach { storedPosts[it.post.id] = it.post }
        val merged =
            (current?.entries.orEmpty() + entries)
                .associateBy { it.post.id }
                .values
                .sortedByDescending { it.similarity }
        val lastFetchedAt =
            entries.maxOfOrNull { it.post.storedAt } ?: current?.lastFetchedAt
        return PostCacheSnapshot(merged, lastFetchedAt).also { snapshots[topic] = it }
    }

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        CachedPostResolution(
            postsById = storedPosts.filterKeys { ids.contains(it) },
            missingIds = ids.filter { !storedPosts.containsKey(it) },
        )

    override suspend fun evict(postId: String) {
        if (postId.isBlank()) return
        storedPosts.remove(postId)
        snapshots.entries.forEach { (topic, snapshot) ->
            val filtered = snapshot.entries.filterNot { it.post.id == postId }
            if (filtered.size != snapshot.entries.size) {
                snapshots[topic] = snapshot.copy(entries = filtered)
            }
        }
    }

    override suspend fun clear(topic: String) {
        snapshots.remove(topic)
    }

    override suspend fun clearAll() {
        snapshots.clear()
    }

    fun prime(
        topic: String,
        snapshot: PostCacheSnapshot,
    ) {
        snapshots[topic] = snapshot
        snapshot.entries.forEach { storedPosts[it.post.id] = it.post }
    }
}

private fun searchResult(id: String, queryOnId: String, similarity: Float = 0.9f): SearchResult =
    searchResult(listOf(SearchResult.Entry(id = id, similarity = similarity)), queryOnId)

private fun searchResult(entries: List<SearchResult.Entry>, queryOnId: String): SearchResult =
    SearchResult(entries, queryOnId)

private fun cachedPost(
    id: String,
    content: String,
    similarity: Float,
    storedAt: Long,
): TopicScopedPost =
    TopicScopedPost(
        CachedPost(
            id = id,
            content = content,
            authorGID = "author",
            storedAt = storedAt,
            authorPublicKey = "",
            authorSignature = "",
            signatureVersion = 1,
        ),
        similarity,
    )
