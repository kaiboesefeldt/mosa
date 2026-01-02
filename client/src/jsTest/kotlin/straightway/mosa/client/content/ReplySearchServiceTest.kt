package straightway.mosa.client.content

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplySearchServiceTest {
    private lateinit var podAccess: RecordingPodAccess
    private lateinit var cache: FakeReplyCache
    private lateinit var logger: ReplyTestLogger
    private lateinit var certificateCache: PodCertificateCache
    private lateinit var timeService: TimeService
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
        cache = FakeReplyCache()
        logger = ReplyTestLogger()
        timeService = FixedTimeService(0)
        certificateCache = PodCertificateCache(timeService)
        window.asDynamic().MOSA_POD_BASE = null
        jwtInvocations.clear()
    }

    @AfterTest
    fun tearDown() {
        window.asDynamic().MOSA_POD_BASE = null
    }

    @Test
    fun `returns cached replies when pod base missing`() = runTest {
        val cached =
            ReplyCacheSnapshot(
                entries =
                    listOf(
                        ThreadScopedReply(
                            CachedPost(
                                id = "p1",
                                content = "cached-content",
                                authorGID = "author",
                                storedAt = 5,
                                rootId = "root-1",
                            ),
                        ),
                    ),
                lastFetchedAt = 5,
            )
        cache.prime("root-1", cached)
        podAccess = RecordingPodAccess()
        val service = createService()

        val replies = service.fetchFor("root-1")

        assertEquals(listOf("p1"), replies.entries.map { it.id })
        assertTrue(podAccess.searchInvocations.isEmpty())
    }

    @Test
    fun `searches and fetches replies sorted by timestamp`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "root-1" to
                            listOf(
                                SearchResult(
                                    entries =
                                        listOf(
                                            SearchResult.Entry("p1", 0.9f),
                                            SearchResult.Entry("p2", 0.8f),
                                        ),
                                    queryOnId = "",
                                ),
                            ),
                    ),
                fetchableEntries =
                    mapOf(
                        "p1" to fetchEntry("p1", "newer", storedAt = 20),
                        "p2" to fetchEntry("p2", "older", storedAt = 10),
                    ),
            )
        val service = createService()

        val replies = service.fetchFor("root-1")

        assertEquals(listOf("p2", "p1"), replies.entries.map { it.id })
    }

    @Test
    fun `paginates replies with time bounded search`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "root-1" to
                            listOf(
                                SearchResult(
                                    entries =
                                        listOf(
                                            SearchResult.Entry("new", 0.9f),
                                            SearchResult.Entry("older", 0.8f),
                                        ),
                                    queryOnId = "",
                                ),
                                SearchResult(
                                    entries = listOf(SearchResult.Entry("even-older", 0.7f)),
                                    queryOnId = "",
                                ),
                            ),
                    ),
                fetchableEntries =
                    mapOf(
                        "new" to fetchEntry("new", "latest", storedAt = 30),
                        "older" to fetchEntry("older", "older-content", storedAt = 10),
                        "even-older" to fetchEntry("even-older", "oldest-content", storedAt = 5),
                    ),
            )
        val service = createService()

        val firstPage = service.fetchFor("root-1")
        val paged = service.fetchMore("root-1")

        assertEquals(
            listOf("older", "new"),
            firstPage.entries.map { it.id },
            "entries=${firstPage.entries.map { it.id }}, fetches=${podAccess.fetchInvocations}",
        )
        assertEquals(listOf("even-older", "older", "new"), paged?.entries?.map { it.id })
        assertEquals(listOf(listOf("new", "older"), listOf("even-older")), podAccess.fetchInvocations.map { it.itemIds })
        assertEquals(listOf(null, 9L), podAccess.searchInvocations.map { it.toTime })
        assertTrue(podAccess.searchOnInvocations.isEmpty())
    }

    @Test
    fun `avoids fetching cached replies`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val cached =
            ReplyCacheSnapshot(
                entries =
                    listOf(
                        ThreadScopedReply(
                            CachedPost(
                                id = "p1",
                                content = "cached",
                                authorGID = "author",
                                storedAt = 1,
                                rootId = "root-1",
                            ),
                        ),
                    ),
                lastFetchedAt = 1,
            )
        cache.prime("root-1", cached)
        podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "root-1" to
                            listOf(
                                SearchResult(
                                    entries = listOf(SearchResult.Entry("p1", 0.9f)),
                                    queryOnId = "",
                                ),
                            ),
                    ),
            )
        val service = createService()

        service.fetchFor("root-1")

        assertTrue(podAccess.fetchInvocations.isEmpty())
    }

    private fun createService(): ReplySearchService =
        ReplySearchService(
            podSessionProvider =
                PodSessionProvider(
                    identityRepository = ReplyIdentityRepository(identity),
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
            replyCache = cache,
            logger = logger,
        )

    private fun fetchEntry(
        id: String,
        content: String,
        storedAt: Long,
    ): FetchResult.Entry =
        FetchResult.Entry(
            id = id,
            content = content,
            authorGID = "author-$id",
            storedAt = storedAt,
            rootId = "root-1",
        )
}

private class RecordingPodAccess(
    responses: Map<String, List<SearchResult>> = emptyMap(),
    private val fetchableEntries: Map<String, FetchResult.Entry> = emptyMap(),
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val searchInvocations = mutableListOf<QueryRequest>()
    val searchOnInvocations = mutableListOf<QueryOnRequest>()
    val fetchInvocations = mutableListOf<FetchRequest>()
    private val responseQueues = responses.mapValues { (_, value) -> value.toMutableList() }
    private val queryOnRoots = mutableMapOf<String, String>()

    override suspend fun approveGid(gid: String): PodCertificate {
        approveInvocations += gid
        return PodCertificate(gid, 100_000, "sig", "inf")
    }

    override suspend fun refreshGid(jwt: String): PodCertificate {
        error("Not implemented")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult {
        searchInvocations += request
        val root = request.threadRootId ?: error("missing thread root id")
        val queue = responseQueues[root] ?: error("no response for $root")
        val result = queue.removeFirstOrNull() ?: error("no response for $root")
        if (result.queryOnId.isNotBlank()) {
            queryOnRoots[result.queryOnId] = root
        }
        return result
    }

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult {
        searchOnInvocations += request
        val root = queryOnRoots[request.queryOnId] ?: error("no query root for ${request.queryOnId}")
        val queue = responseQueues[root] ?: error("no response for $root")
        val result = queue.removeFirstOrNull() ?: error("no response for $root")
        if (result.queryOnId.isNotBlank()) {
            queryOnRoots[result.queryOnId] = root
        }
        return result
    }

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult {
        fetchInvocations += request
        val entries =
            request.itemIds.map { id ->
                fetchableEntries[id] ?: error("no entry for $id")
            }
        return FetchResult(entries)
    }
}

private class FakeReplyCache : ReplyCache {
    private val snapshots = mutableMapOf<String, ReplyCacheSnapshot>()
    private val storedPosts = mutableMapOf<String, CachedPost>()

    override suspend fun snapshot(rootId: String): ReplyCacheSnapshot =
        snapshots[rootId] ?: ReplyCacheSnapshot(emptyList(), null)

    override suspend fun append(
        rootId: String,
        entries: List<ThreadScopedReply>,
    ): ReplyCacheSnapshot {
        if (entries.isEmpty()) {
            return snapshots[rootId] ?: ReplyCacheSnapshot(emptyList(), null)
        }
        val current = snapshots[rootId]
        entries.forEach { storedPosts[it.post.id] = it.post }
        val merged =
            (current?.entries.orEmpty() + entries)
                .associateBy { it.post.id }
                .values
                .sortedBy { it.post.storedAt }
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt } ?: current?.lastFetchedAt
        return ReplyCacheSnapshot(merged, lastFetchedAt).also { snapshots[rootId] = it }
    }

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        CachedPostResolution(
            postsById = storedPosts.filterKeys { ids.contains(it) },
            missingIds = ids.filter { !storedPosts.containsKey(it) },
        )

    override suspend fun clear(rootId: String) {
        snapshots.remove(rootId)
    }

    fun prime(
        rootId: String,
        snapshot: ReplyCacheSnapshot,
    ) {
        snapshots[rootId] = snapshot
        snapshot.entries.forEach { storedPosts[it.post.id] = it.post }
    }
}

private class ReplyIdentityRepository(
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

private class ReplyTestLogger : Logger {
    override fun error(message: String, throwable: Throwable?) {}

    override fun warn(message: String, throwable: Throwable?) {}
}

private class FixedTimeService(
    override val now: Long,
) : TimeService
