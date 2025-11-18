package straightway.mosa.client.topic

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.logging.Logger
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.ReplyRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicSearchServiceTest {
    private lateinit var podAccess: FakePodAccess
    private lateinit var logger: RecordingLogger
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
    fun `queries every topic with jwt token`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        assertEquals("https://pod.example", PodBaseProvider().base())
        podAccess =
            FakePodAccess(
                responses =
                    mapOf(
                        "Alpha" to queryResult("p1"),
                        "Beta" to queryResult("p2"),
                    ),
            )
        val service = createService()
        val topics = listOf(Topic("Alpha"), Topic("Beta"))

        val results = service.fetchFor(topics)

        assertEquals(1, podAccess.approveInvocations.size)
        assertEquals(identity.gid, podAccess.approveInvocations.single())
        val tokenInvocation = jwtInvocations.single()
        assertEquals(identity.gid, tokenInvocation.first.gid)
        assertEquals(identity.gid, tokenInvocation.second.gid)
        assertEquals(2, podAccess.queryInvocations.size)
        assertEquals(listOf("Alpha", "Beta"), podAccess.queryInvocations.map { it.topic })
        assertEquals("p1", results.first().entries.first().id)
        assertEquals("p2", results.last().entries.first().id)
    }

    @Test
    fun `skips topic when query fails`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        assertEquals("https://pod.example", PodBaseProvider().base())
        podAccess =
            FakePodAccess(
                responses = mapOf("Alpha" to queryResult("ok")),
                errorForTopic = "Beta",
            )
        val service = createService()
        val topics = listOf(Topic("Alpha"), Topic("Beta"))

        val results = service.fetchFor(topics)

        assertEquals(1, results.size)
        assertTrue(logger.warnMessages.any { it.contains("Beta") })
    }

    private fun createService(): TopicSearchService =
        TopicSearchService(
            identityRepository = StaticIdentityRepository(identity),
            podBaseProvider = PodBaseProvider(),
            podAccessFactory = { podAccess },
            jwtBuilder = { identity, cert ->
                jwtInvocations += identity to cert
                "generated-jwt"
            },
            logger = logger,
        )

    private fun queryResult(id: String): QueryResult =
        QueryResult(
            entries = listOf(QueryResult.Entry(id, "content-$id", 0.9f, "author", 0)),
            queryOnId = "query-on-$id",
        )

    private fun clearBase() {
        window.asDynamic().MOSA_POD_BASE = null
    }
}

private class FakePodAccess(
    private val responses: Map<String, QueryResult> = emptyMap(),
    private val errorForTopic: String? = null,
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val queryInvocations = mutableListOf<QueryRequest>()

    override suspend fun approveGid(gid: String): PodCertificate {
        approveInvocations += gid
        return PodCertificate(gid, 1, "sig", "inf")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        error("Not implemented")
    }

    override suspend fun query(request: QueryRequest, jwt: String): QueryResult {
        queryInvocations += request
        val topic = request.topic ?: error("topic missing")
        if (topic == errorForTopic) throw RuntimeException("boom")
        return responses[topic] ?: error("no response for $topic")
    }

    override suspend fun queryOn(request: QueryOnRequest, jwt: String): QueryResult {
        error("Not implemented")
    }
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1)

    override suspend fun movePrevious(): IdentitySelection? = identitySelection()

    override suspend fun moveNext(): IdentitySelection = identitySelection()

    override suspend fun deleteCurrent(): IdentitySelection = identitySelection()

    override suspend fun select(index: Int): IdentitySelection? = identitySelection()

    override suspend fun import(identity: Identity): IdentitySelection = identitySelection()

    private fun identitySelection(): IdentitySelection = IdentitySelection(identity, 0, 1)
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
