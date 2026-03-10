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
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.pod.PodCertificateCache
import straightway.mosa.client.pod.InMemoryPodCertificateStore
import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.client.pod.PodCertificateStore
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.content.CachedPost
import straightway.mosa.client.content.CachedPostResolution
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostCacheSnapshot
import straightway.mosa.client.content.TopicScopedPost
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.TopicStore
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.ReplyCacheSnapshot
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.ThreadScopedReply
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator

class IdentityConnectedViewTest {
    private var originalPrompt: dynamic = null
    private var originalObserver: dynamic = null
    private lateinit var sessionStore: ConnectedViewIdentitySessionStore

    @BeforeTest
    fun setUp() {
        startKoinForTest(emptyList(), RecordingPodAccess(emptyMap()))
        originalPrompt = window.asDynamic().prompt
        originalObserver = window.asDynamic().IntersectionObserver
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        window.asDynamic().prompt = originalPrompt
        window.asDynamic().IntersectionObserver = originalObserver
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
    fun `appends posts when reaching list end`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val triggers = stubIntersectionObserver()
        val podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "Alpha" to
                            QueryResult(
                                entries =
                                    listOf(
                                        QueryResult.Entry("p1", "content-1", 0.95f, "author", 0),
                                        QueryResult.Entry("p2", "content-2", 0.9f, "author", 0),
                                    ),
                                queryOnId = "q1",
                            ),
                    ),
                additionalResponses =
                    mapOf(
                        "Alpha" to
                            listOf(
                                QueryResult(
                                    entries =
                                        listOf(
                                            QueryResult.Entry("p3", "content-3", 0.82f, "author", 0),
                                        ),
                                    queryOnId = "q2",
                                ),
                            ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )

        val view = renderConnectedView()

        waitForCondition("initial posts") { view.container.querySelectorAll(".post-card").length == 2 }
        waitForCondition("observer registered") { triggers.isNotEmpty() }
        scrollPostsToEnd(view.container)
        triggers.toList().forEach { it() }
        waitForCondition("pagination query sent") { podAccess.queryInvocations.size == 2 }
        waitForCondition("posts appended") { view.container.querySelectorAll(".post-card").length == 3 }
        assertEquals(listOf(null, 0.9f), podAccess.queryInvocations.map { it.scoreMustBeBelow })
    }

    @Test
    fun `does not paginate after empty page`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val triggers = stubIntersectionObserver()
        val podAccess =
            RecordingPodAccess(
                responses =
                    mapOf(
                        "Alpha" to
                            QueryResult(
                                entries =
                                    listOf(
                                        QueryResult.Entry("p1", "content-1", 0.95f, "author", 0),
                                        QueryResult.Entry("p2", "content-2", 0.9f, "author", 0),
                                    ),
                                queryOnId = "q1",
                            ),
                    ),
                additionalResponses = mapOf("Alpha" to listOf(QueryResult(emptyList(), "q2"))),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )

        val view = renderConnectedView()

        waitForCondition("initial posts") { view.container.querySelectorAll(".post-card").length == 2 }
        waitForCondition("observer registered") { triggers.isNotEmpty() }
        scrollPostsToEnd(view.container)
        triggers.toList().forEach { it() }
        waitForCondition("pagination query sent") { podAccess.queryInvocations.size == 2 }
        triggers.toList().forEach { it() }
        delay(100)
        assertEquals(2, podAccess.queryInvocations.size)
        assertEquals(listOf(null, 0.9f), podAccess.queryInvocations.map { it.scoreMustBeBelow })
    }

    @Test
    fun `loads replies when selecting a post`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("root-1")),
                replyResponses =
                    mapOf(
                        "root-1" to
                            listOf(
                                QueryResult(
                                    entries =
                                        listOf(
                                            QueryResult.Entry("r-1", "older", 0.7f, "author", 5, "root-1"),
                                            QueryResult.Entry("r-2", "newer", 0.9f, "author", 10, "root-1"),
                                        ),
                                    queryOnId = "",
                                ),
                            ),
                    ),
            )
        overrideDependencies(topics = listOf(Topic("Alpha")), podAccess = podAccess)
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".post-card") != null }
        val postCard = view.container.querySelector(".post-card") ?: error("post missing")
        user.click(postCard).await()

        waitForCondition { view.container.querySelectorAll(".reply-card").length == 2 }
        val replies = view.container.querySelectorAll(".reply-card__content")
        assertEquals("older", replies.item(0)?.textContent?.trim())
        assertEquals("newer", replies.item(1)?.textContent?.trim())
        assertTrue(podAccess.searchInvocations.any { it.threadRootId == "root-1" })
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
                additionalResponses = mapOf("Alpha" to listOf(queryResult("p1"))),
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
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        assertTrue(podAccess.queryInvocations.size >= 1)
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
        assertEquals(3, podAccess.queryInvocations.size)
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

    private suspend fun waitForCondition(label: String? = null, condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            delay(20)
        }
        val suffix = label?.let { ": $it" } ?: ""
        error("condition not met$suffix")
    }

    @Suppress("unused")
    private fun stubIntersectionObserver(): MutableList<() -> Unit> {
        val triggers = mutableListOf<() -> Unit>()
        window.asDynamic().IntersectionObserver =
            { callback: dynamic, _: dynamic ->
                val observer = js("{}")
                observer.observe = { target: dynamic ->
                    triggers += {
                        callback(
                            arrayOf(
                                js(
                                    "{ isIntersecting: true, target: target }"
                                )
                            ),
                            observer,
                        )
                    }
                }
                observer.disconnect = {}
                observer
            }
        return triggers
    }

private fun scrollPostsToEnd(container: Element) {
    val column = container.querySelector(".topic-board__column--posts") ?: error("Posts column missing")
    column.setAttribute("data-mosa-scroll-ready", "true")
    column.asDynamic().scrollTop = 10
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
                single<ReplyCache> { InMemoryReplyCache() }
                single { TopicRepository(get(), get()) }
                single<IdentitySelectionRepository> { StaticIdentityRepository(identityForTests) }
                single { TimeService() }
                single { PodBaseProvider() }
                single<PodAccessFactory> { { podAccess } }
                single<JwtBuilder> { { _, _ -> "jwt" } }
                single<Logger> { RecordingLogger() }
                single<PodCertificateStore> { InMemoryPodCertificateStore() }
                single { PodCertificateCache(get(), get()) }
                single {
                    PodSessionProvider(
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                    )
                }
                single { PostPublisher(get()) }
                single { PostDeletionService(get(), get(), get()) }
                single { AllPostsDeletionService(get(), get(), get()) }
                single { ReplyPublisher(get()) }
                single { TopicSearchService(get(), get(), get()) }
                single { ReplySearchService(get(), get(), get()) }
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
        statusText = null,
        canDelete = false,
        canDeletePosts = false,
        canExportPosts = false,
        canFreezePosts = false,
        canRestorePosts = false,
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
        onDeletePosts = {},
        onExportPosts = {},
        onFreezePosts = {},
        onRestorePosts = {},
        onSelect = {},
        canDownload = false,
        onDownload = {},
        canCopy = false,
        onCopy = {},
        canUpload = false,
        onUpload = {},
        canPaste = false,
        onPaste = {},
        onPasteContent = {},
        isPersisted = true,
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
    responses: Map<String, QueryResult>,
    private val additionalResponses: Map<String, List<QueryResult>> = emptyMap(),
    replyResponses: Map<String, List<QueryResult>> = emptyMap(),
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val searchInvocations = mutableListOf<QueryRequest>()
    val queryInvocations get() = searchInvocations
    val publishRequests = mutableListOf<straightway.mosa.content.PublishRequest>()
    val replyRequests = mutableListOf<straightway.mosa.content.ReplyRequest>()
    var shouldFailPublish = false
    private val responsesByTopic: MutableMap<String, MutableList<QueryResult>> =
        (responses.keys + additionalResponses.keys)
            .associateWith { topic ->
                mutableListOf<QueryResult>().apply {
                    responses[topic]?.let { add(it) }
                    addAll(additionalResponses[topic].orEmpty())
                    if (isEmpty()) error("no response for $topic")
                }
            }.toMutableMap()
    private val responsesByRoot = replyResponses.mapValues { (_, value) -> value.toMutableList() }.toMutableMap()
    private val entriesById: Map<String, QueryResult.Entry> =
        (responsesByTopic.values.flatten() + responsesByRoot.values.flatten())
            .flatMap { it.entries }
            .associateBy { it.id }
    private val queryOnRoots = mutableMapOf<String, String>()

    override suspend fun approveGid(gid: String): PodCertificateResponse {
        approveInvocations += gid
        return PodCertificateResponse(
            certificate = PodCertificate(gid, 100_000, "sig", "info"),
            refreshRecommendedAt = 50_000,
        )
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        error("Not implemented")
    }

    override suspend fun publish(request: straightway.mosa.content.PublishRequest, jwt: String) {
        if (shouldFailPublish) throw RuntimeException("publish failed")
        publishRequests += request
    }

    override suspend fun reply(request: straightway.mosa.content.ReplyRequest, jwt: String) {
        replyRequests += request
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
        val topic = request.topic
        val rootId = request.threadRootId
        if (topic != null) {
            val queue = responsesByTopic[topic] ?: error("no response for $topic")
            val response =
                runCatching { queue.removeAt(0) }
                    .getOrElse { error("no response for $topic") }
            return response.toSearchResult()
        }
        if (rootId != null) {
            val queue = responsesByRoot[rootId] ?: error("no response for $rootId")
            val response =
                runCatching { queue.removeAt(0) }
                    .getOrElse { error("no response for $rootId") }
            if (response.queryOnId.isNotBlank()) {
                queryOnRoots[response.queryOnId] = rootId
            }
            return response.toSearchResult()
        }
        error("topic missing")
    }

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult {
        val root = queryOnRoots[request.queryOnId] ?: error("no response for ${request.queryOnId}")
        val queue = responsesByRoot[root] ?: error("no response for $root")
        val response =
            runCatching { queue.removeAt(0) }
                .getOrElse { error("no response for $root") }
        if (response.queryOnId.isNotBlank()) {
            queryOnRoots[response.queryOnId] = root
        }
        return response.toSearchResult()
    }

    override suspend fun fetch(request: straightway.mosa.content.FetchRequest, jwt: String): FetchResult {
        val entries =
            request.itemIds.map { id ->
                val entry = entriesById[id] ?: error("no entry for $id")
                FetchResult.Entry(
                    id = entry.id,
                    content = entry.content,
                    authorGID = entry.authorGID,
                    storedAt = entry.storedAt,
                    rootId = entry.rootId,
                )
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
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt } ?: current?.lastFetchedAt
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
}

private class InMemoryReplyCache : ReplyCache {
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

    override suspend fun evict(postId: String) {
        if (postId.isBlank()) return
        storedPosts.remove(postId)
        snapshots.entries.forEach { (rootId, snapshot) ->
            val filtered = snapshot.entries.filterNot { it.post.id == postId }
            if (filtered.size != snapshot.entries.size) {
                snapshots[rootId] = snapshot.copy(entries = filtered)
            }
        }
    }

    override suspend fun clear(rootId: String) {
        snapshots.remove(rootId)
    }
}

private class StaticIdentityRepository(
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

private fun QueryResult.toSearchResult(): SearchResult =
    SearchResult(
        entries = entries.map { SearchResult.Entry(it.id, it.similarity) },
        queryOnId = queryOnId,
    )
