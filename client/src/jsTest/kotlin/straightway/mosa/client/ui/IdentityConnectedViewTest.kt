package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
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
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.content.CachedPost
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostCacheSnapshot
import straightway.mosa.client.content.TopicScopedPost
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.TopicStore
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator

class IdentityConnectedViewTest {
    private var originalPrompt: dynamic = null
    private lateinit var sessionStore: ConnectedViewIdentitySessionStore

    @BeforeTest
    fun setUp() {
        startKoinForTest(emptyList(), RecordingPodAccess(emptyMap()))
        originalPrompt = window.asDynamic().prompt
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        window.asDynamic().prompt = originalPrompt
        sessionStore.prime(IdentitySessionSnapshot(null, false))
        window.asDynamic().MOSA_POD_BASE = null
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
        val cardTitle = view.container.querySelector(".topic-card__title")?.textContent?.trim()
        assertEquals("New topic", cardTitle)
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
        waitForCondition { sessionStore.current()?.isProfileVisible == true }
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
        val postContent = view.container.querySelector(".post-card__content")?.textContent?.trim()
        assertEquals("content-p1", postContent)
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
        val postContent = view.container.querySelector(".post-card__content")?.textContent?.trim()
        assertEquals("content-p2", postContent)
    }

    @Test
    fun `restores selected topic from session store`() = runTest {
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
        sessionStore.save(IdentitySessionSnapshot("gid-1", false, "Beta"))

        val view = renderConnectedView()

        waitForCondition { podAccess.queryInvocations.isNotEmpty() }
        waitForCondition { view.container.querySelectorAll(".topic-card").length == 2 }
        assertEquals(listOf("Beta"), podAccess.queryInvocations.map { it.topic })
        waitForCondition { topicCard(view.container, "Beta").classList.contains("topic-card--selected") }
        val selected = topicCard(view.container, "Beta")
        assertTrue(selected.classList.contains("topic-card--selected"))
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

    @Test
    fun `reloads posts when filter removed`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { podAccess.queryInvocations.size == 1 }
        waitForCondition { view.container.querySelector(".post-card__filter") != null }
        user.click(filterButton(view.container)).await()
        waitForCondition { view.container.querySelector(".topic-board__filter-indicator") != null }
        delay(100)
        assertEquals(1, podAccess.queryInvocations.size)

        user.click(filterResetButton(view.container)).await()
        waitForCondition { podAccess.queryInvocations.size == 2 }
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
    }

    @Test
    fun `does not reload filtered topic when reselected`() = runTest {
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

        waitForCondition { podAccess.queryInvocations.size == 1 }
        waitForCondition { view.container.querySelector(".post-card__filter") != null }
        user.click(filterButton(view.container)).await()
        waitForCondition { view.container.querySelector(".topic-board__filter-indicator") != null }
        waitForCondition { view.container.querySelectorAll(".topic-card").length == 2 }
        user.click(topicCard(view.container, "Beta")).await()
        waitForCondition { podAccess.queryInvocations.size == 2 }

        user.click(topicCard(view.container, "Alpha")).await()
        delay(100)
        assertEquals(2, podAccess.queryInvocations.size)
    }

    @Test
    fun `opens composer dialog from floating button`() = runTest {
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".composer-button") != null }
        user.click(composerButton(view.container)).await()

        waitForCondition { view.container.querySelector(".composer-dialog") != null }
    }

    @Test
    fun `publishes post via composer`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".composer-button") != null }
        user.click(composerButton(view.container)).await()
        waitForCondition { view.container.querySelector(".composer-dialog__textarea") != null }
        user.type(composerTextarea(view.container), "Neuer Beitrag").await()
        user.click(sendComposerButton(view.container)).await()

        waitForCondition { podAccess.publishRequests.isNotEmpty() }
        assertEquals("Neuer Beitrag", podAccess.publishRequests.single().text)
        waitForCondition { view.container.querySelector(".composer-dialog") == null }
    }

    @Test
    fun `deleting selected topic selects next`() = runTest {
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
        user.click(topicDeleteButton(view.container, "Alpha")).await()

        waitForCondition { podAccess.queryInvocations.size == 2 }
        assertEquals("Beta", podAccess.queryInvocations.last().topic)
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        val postContent = view.container.querySelector(".post-card__content")?.textContent?.trim()
        assertEquals("content-p2", postContent)
    }

    @Test
    fun `deleting last topic clears posts`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        user.click(topicDeleteButton(view.container, "Alpha")).await()

        waitForCondition { podAccess.queryInvocations.size == 1 }
        waitForCondition { view.container.querySelectorAll(".post-card").length == 0 }
        waitForCondition { view.container.querySelector(".topic-board__column--posts .topic-board__empty") != null }
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
            val label = node.querySelector(".topic-card__title")?.textContent?.trim()
            if (label == title) return node
        }
        error("Topic card $title missing")
    }

    private fun topicDeleteButton(container: Element, title: String): Element =
        topicCard(container, title).querySelector(".topic-card__delete")
            ?: error("Delete button for $title missing")

    private fun filterButton(container: Element): Element =
        container.querySelector(".post-card__filter") ?: error("Filter button missing")

    private fun filterResetButton(container: Element): Element =
        container.querySelector(".topic-board__filter-reset") ?: error("Filter reset button missing")

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
                single<IdentitySessionStore> { ConnectedViewIdentitySessionStore() }
                single { FictionalNameGenerator() }
                single { DiceBearAvatarGenerator() }
                single<TopicStore> { InMemoryTopicStore(topics) }
                single<PostCache> { InMemoryPostCache() }
                single { TopicRepository(get(), get()) }
                single<IdentitySelectionRepository> { StaticIdentityRepository(identityForTests) }
                single { PodBaseProvider() }
                single<PodAccessFactory> { { podAccess } }
                single<JwtBuilder> { { _, _ -> "jwt" } }
                single<Logger> { RecordingLogger() }
                single { PostPublisher(get(), get(), get(), get(), get()) }
                single { TopicSearchService(get(), get(), get(), get(), get(), get()) }
            }
                startKoin { modules(module) }
        sessionStore = ConnectedViewTestInjector.get<IdentitySessionStore>() as ConnectedViewIdentitySessionStore
        sessionStore.prime(IdentitySessionSnapshot(null, false))
    }

    private fun avatarButton(container: Element): Element =
        container.querySelector(".connected-shell__avatar-button")
            ?: error("Avatar button missing")

    private fun composerButton(container: Element): Element =
        container.querySelector(".composer-button") ?: error("Composer button missing")

    private fun composerTextarea(container: Element): Element =
        container.querySelector(".composer-dialog__textarea") ?: error("Composer textarea missing")

    private fun sendComposerButton(container: Element): Element =
        container.querySelector(".composer-dialog__send") ?: error("Composer send button missing")
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
        canCopy = false,
        onCopy = {},
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

private class ConnectedViewIdentitySessionStore : IdentitySessionStore {
    private var snapshot: IdentitySessionSnapshot? = null

    override suspend fun load(): IdentitySessionSnapshot? = snapshot

    override suspend fun save(snapshot: IdentitySessionSnapshot) {
        this.snapshot = snapshot
    }

    fun prime(snapshot: IdentitySessionSnapshot?) {
        this.snapshot = snapshot
    }

    fun current(): IdentitySessionSnapshot? = snapshot
}

private class RecordingPodAccess(
    private val responses: Map<String, QueryResult>,
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val queryInvocations = mutableListOf<QueryRequest>()
    val publishRequests = mutableListOf<straightway.mosa.content.PublishRequest>()
    var shouldFailPublish = false

    override suspend fun approveGid(gid: String): PodCertificate {
        approveInvocations += gid
        return PodCertificate(gid, 1, "sig", "info")
    }

    override suspend fun publish(request: straightway.mosa.content.PublishRequest, jwt: String) {
        if (shouldFailPublish) throw RuntimeException("publish failed")
        publishRequests += request
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

private class InMemoryPostCache : PostCache {
    private val snapshots = mutableMapOf<String, PostCacheSnapshot>()

    override suspend fun snapshot(topic: String): PostCacheSnapshot =
        snapshots[topic] ?: PostCacheSnapshot(emptyList(), null)

    override suspend fun append(topic: String, entries: List<QueryResult.Entry>): PostCacheSnapshot {
        val current = snapshots[topic]
        if (entries.isEmpty()) {
            return current ?: PostCacheSnapshot(emptyList(), null)
        }
        val merged =
            (current?.entries.orEmpty().map { it.asQueryResultEntry() } + entries)
                .associateBy { it.id }
                .values
                .sortedByDescending { it.similarity }
                .map { entry -> entry.toCacheEntry() }
        val lastFetchedAt = entries.maxOfOrNull { it.storedAt } ?: current?.lastFetchedAt
        return PostCacheSnapshot(merged, lastFetchedAt).also { snapshots[topic] = it }
    }

    override suspend fun clear(topic: String) {
        snapshots.remove(topic)
    }

    private fun QueryResult.Entry.toCacheEntry(): TopicScopedPost =
        TopicScopedPost(
            CachedPost(
                id = id,
                content = content,
                authorGID = authorGID,
                storedAt = storedAt,
                rootId = rootId,
            ),
            similarity,
        )
}

private class StaticIdentityRepository(
    private val identity: Identity,
) : IdentitySelectionRepository {
    override suspend fun loadOrCreate(): IdentitySelection =
        IdentitySelection(identity, 0, 1)

    override suspend fun movePrevious(): IdentitySelection = selection()

    override suspend fun moveNext(): IdentitySelection = selection()

    override suspend fun deleteCurrent(): IdentitySelection = selection()

    override suspend fun select(index: Int): IdentitySelection = selection()

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
