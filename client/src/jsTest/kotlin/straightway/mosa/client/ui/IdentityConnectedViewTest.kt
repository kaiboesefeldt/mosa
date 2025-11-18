package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.w3c.dom.Element
import react.FC
import react.Props
import react.create
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent
import straightway.mosa.client.topic.JwtBuilder
import straightway.mosa.client.topic.PodAccessFactory
import straightway.mosa.client.topic.Topic
import straightway.mosa.client.topic.TopicRepository
import straightway.mosa.client.topic.TopicSearchService
import straightway.mosa.client.topic.TopicStore
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalName

class IdentityConnectedViewTest {
    private var originalPrompt: dynamic = null
    private lateinit var sessionStore: IdentitySessionStore

    @BeforeTest
    fun setUp() {
        startKoinForTest(emptyList(), RecordingPodAccess(emptyMap()))
        originalPrompt = window.asDynamic().prompt
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        window.asDynamic().prompt = originalPrompt
        sessionStore.save(IdentitySessionSnapshot(null, false))
        stopKoin()
    }

    @Test
    fun `adds topic via prompt`() = runTest {
        window.asDynamic().prompt = { _: String -> "New topic" }
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".topic-board__add-button") != null }
        val button = addButton(view.container)
        user.click(button).await()
        waitForCondition { view.container.querySelectorAll(".topic-card").length >= 1 }
        val cardText = view.container.querySelector(".topic-card")?.textContent?.trim()
        assertEquals("New topic", cardText)
    }

    @Test
    fun `restores profile popover visibility from session store`() = runTest {
        sessionStore.save(IdentitySessionSnapshot("gid-1", true))
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".profile-dialog") != null }
    }

    @Test
    fun `persists profile popover visibility on toggle`() = runTest {
        sessionStore.save(IdentitySessionSnapshot("gid-2", false))
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".profile-dialog") == null }
        user.click(avatarButton(view.container)).await()
        waitForCondition { sessionStore.load()?.isProfileVisible == true }
    }

    @Test
    fun `loads posts only for initial selection`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "Alpha" to queryResult("p1"),
                        "Beta" to queryResult("p2"),
                    ),
            )
        overrideDependencies(
            topics =
                listOf(
                    Topic("Alpha"),
                    Topic("Beta"),
                ),
            podAccess = podAccess,
        )

        val view = renderConnectedView()

        waitForCondition { podAccess.queryInvocations.isNotEmpty() }
        assertEquals(listOf("Alpha"), podAccess.queryInvocations.map { it.topic })
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        val topicTitle = view.container.querySelector(".post-card__topic")?.textContent?.trim()
        assertEquals("Alpha", topicTitle)
    }

    @Test
    fun `loads posts after switching selection`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "Alpha" to queryResult("p1"),
                        "Beta" to queryResult("p2"),
                    ),
            )
        overrideDependencies(
            topics =
                listOf(
                    Topic("Alpha"),
                    Topic("Beta"),
                ),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { podAccess.queryInvocations.isNotEmpty() }
        waitForCondition { view.container.querySelectorAll(".topic-card").length == 2 }
        user.click(topicCard(view.container, "Beta")).await()

        waitForCondition { podAccess.queryInvocations.size == 2 }
        assertEquals("Beta", podAccess.queryInvocations.last().topic)
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        val topicTitle = view.container.querySelector(".post-card__topic")?.textContent?.trim()
        assertEquals("Beta", topicTitle)
    }

    @Test
    fun `queries pod only when selection changes`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "Alpha" to queryResult("p1"),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { podAccess.queryInvocations.size == 1 }
        waitForCondition { view.container.querySelectorAll(".topic-card").length == 1 }
        user.click(topicCard(view.container, "Alpha")).await()
        delay(50)

        assertEquals(1, podAccess.queryInvocations.size)
    }

    private suspend fun waitForCondition(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(20)
        }
        error("condition not met")
    }

    private fun addButton(container: Element): Element =
        container.querySelector(".topic-board__add-button") ?: error("Add button missing")

    private fun renderConnectedView(): RenderResult =
        render(
            ConnectedViewHarness.create(),
        )

    private fun topicCard(container: Element, title: String): Element {
        val nodes = container.querySelectorAll(".topic-card")
        repeat(nodes.length) { index ->
            val node = nodes.item(index) as? Element ?: return@repeat
            if (node?.textContent?.trim() == title) return node
        }
        error("Topic card $title missing")
    }

    private fun overrideDependencies(
        topics: List<Topic>,
        podAccess: RecordingPodAccess,
    ) {
        startKoinForTest(topics, podAccess)
    }

    private fun startKoinForTest(
        topics: List<Topic>,
        podAccess: RecordingPodAccess,
    ) {
        stopKoin()
        val module =
            module {
                single { Translator(emptyList()) }
                single<IdentitySessionStore> { BrowserIdentitySessionStore() }
                single<TopicStore> { InMemoryTopicStore(topics) }
                single { TopicRepository(get()) }
                single<IdentitySelectionRepository> { StaticIdentityRepository(identityForTests) }
                single { PodBaseProvider() }
                single<PodAccessFactory> { { podAccess } }
                single<JwtBuilder> { { _, _ -> "jwt" } }
                single<Logger> { RecordingLogger() }
                single { TopicSearchService(get(), get(), get(), get(), get()) }
            }
        startKoin { modules(module) }
        sessionStore = ConnectedViewTestInjector.get()
        sessionStore.save(IdentitySessionSnapshot(null, false))
    }

    private fun avatarButton(container: Element): Element =
        container.querySelector(".connected-shell__avatar-button")
            ?: error("Avatar button missing")
}

private val ConnectedViewHarness = FC<Props> {
    IdentityConnectedView {
        identityName = "Tester"
        profile = placeholderProfile()
    }
}

private object ConnectedViewTestInjector : KoinComponent

private fun placeholderProfile(): IdentityScreen.Profile =
    IdentityScreen.Profile(
        identityNameParts = FictionalName(emptyList(), ""),
        identityAvatar = null,
        isBusy = false,
        errorText = null,
        canDelete = false,
        canNavigatePrevious = false,
        canNavigateNext = false,
        isShowingLastIdentity = false,
        identityIndex = 0,
        identityTotal = 1,
        canConnect = false,
        onConnect = {},
        onPrevious = {},
        onNext = {},
        onDelete = {},
        onSelect = {},
        canDownload = false,
        onDownload = {},
        canUpload = false,
        onUpload = {},
    )

private val identityForTests =
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

private class RecordingPodAccess(
    private val responses: Map<String, QueryResult>,
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val queryInvocations = mutableListOf<QueryRequest>()

    override suspend fun approveGid(gid: String): PodCertificate {
        approveInvocations += gid
        return PodCertificate(gid, 1, "sig", "info")
    }

    override suspend fun publish(request: straightway.mosa.content.PublishRequest, jwt: String) {
        error("not used")
    }

    override suspend fun reply(request: straightway.mosa.content.ReplyRequest, jwt: String) {
        error("not used")
    }

    override suspend fun query(request: QueryRequest, jwt: String): QueryResult {
        queryInvocations += request
        val topic = request.topic ?: error("topic missing")
        return responses[topic] ?: error("no response for $topic")
    }

    override suspend fun queryOn(request: straightway.mosa.content.QueryOnRequest, jwt: String): QueryResult {
        error("not used")
    }
}

private class InMemoryTopicStore(
    private var topics: List<Topic>,
) : TopicStore {
    override suspend fun load(): List<Topic> = topics

    override suspend fun save(topics: List<Topic>) {
        this.topics = topics
    }
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1)

    override suspend fun movePrevious(): IdentitySelection? = selection()

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection? = selection()

    override suspend fun import(identity: Identity): IdentitySelection = selection()

    private fun selection(): IdentitySelection = IdentitySelection(identity, 0, 1)
}

private class RecordingLogger : Logger {
    override fun error(message: String, throwable: Throwable?) {}

    override fun warn(message: String, throwable: Throwable?) {}
}

private fun queryResult(id: String): QueryResult =
    QueryResult(
        entries =
            listOf(
                QueryResult.Entry(id, "content-$id", 0.9f, "author", 0),
            ),
        queryOnId = "query-on-$id",
    )
