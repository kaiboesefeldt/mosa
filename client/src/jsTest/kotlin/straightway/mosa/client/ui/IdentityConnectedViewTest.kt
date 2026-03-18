package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLImageElement
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
import react.dom.html.ReactHTML.button
import react.useState
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.Base64.toBase64Url
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
import straightway.mosa.client.content.ContentCapabilitiesService
import straightway.mosa.client.content.TopicScopedPost
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.TopicStore
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.client.content.ContentSigner
import straightway.mosa.client.content.ContentSignatureVerifier
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.ReplyCacheSnapshot
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.ThreadScopedReply
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.SearchResult
import straightway.mosa.content.ContentCapabilitiesResult
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult
import straightway.mosa.message.CreateAdminMessageRequest

class IdentityConnectedViewTest {
    private var originalPrompt: dynamic = null
    private var originalObserver: dynamic = null
    private lateinit var sessionStore: ConnectedViewIdentitySessionStore
    private lateinit var inboxStateStore: InMemoryAdminMessageInboxStateStore

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
        inboxStateStore.clear()
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
    fun `reloads posts after snapshot refresh version changes`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                additionalResponses = mapOf("Alpha" to listOf(queryResult("p2"))),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderRefreshableConnectedView()

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        assertEquals("content-p1", view.container.querySelector(".post-card__content")?.textContent?.trim())

        user.click(snapshotRefreshButton(view.container)).await()

        waitForCondition { podAccess.queryInvocations.size == 2 }
        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        assertEquals("content-p2", view.container.querySelector(".post-card__content")?.textContent?.trim())
    }

    @Test
    fun `shows delete action for foreign post when connected identity is admin`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1", authorGid = "other-author")),
                capabilities = ContentCapabilitiesResult(isAdmin = true),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, false))

        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".post-card__delete") != null }
    }

    @Test
    fun `shows admin badge and tooltip on connected avatar when connected identity is admin`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                capabilities = ContentCapabilitiesResult(isAdmin = true),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, false))

        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".connected-shell__avatar-badge") != null }
        assertEquals(
            text("identity.admin.badge"),
            view.container.querySelector(".connected-shell__avatar-badge")?.textContent?.trim(),
        )
        assertEquals(
            text("connected.profile.avatar.admin.title"),
            avatarButton(view.container).getAttribute("title"),
        )
    }

    @Test
    fun `shows admin badge again when returning to connected admin profile`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                capabilitiesByGid =
                    mapOf(
                        identityForTests.gid to ContentCapabilitiesResult(isAdmin = true),
                        "gid-other" to ContentCapabilitiesResult(isAdmin = false),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, true))
        val user = userEvent.setup()
        val view = renderAdminProfileCycleView()

        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") != null }
        user.click(showOtherProfileButton(view.container)).await()
        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") == null }
        user.click(showAdminProfileButton(view.container)).await()
        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") != null }
    }

    @Test
    fun `shows admin badge when browsing from non admin profile to admin profile`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                capabilitiesByGid =
                    mapOf(
                        identityForTests.gid to ContentCapabilitiesResult(isAdmin = true),
                        "gid-other" to ContentCapabilitiesResult(isAdmin = false),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, true))
        val user = userEvent.setup()
        val view = renderAdminProfileCycleView(startWithOtherProfile = true)

        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") == null }
        user.click(showAdminProfileButton(view.container)).await()
        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") != null }
    }

    @Test
    fun `ignores stale displayed admin response after switching profiles`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val connectedAdmin = CompletableDeferred(ContentCapabilitiesResult(isAdmin = true))
        val otherProfile = CompletableDeferred<ContentCapabilitiesResult>()
        val displayedAdmin = CompletableDeferred<ContentCapabilitiesResult>()
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                deferredCapabilitiesByGid =
                    mapOf(
                        identityForTests.gid to mutableListOf(connectedAdmin, displayedAdmin),
                        "gid-other" to mutableListOf(otherProfile),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, true))
        val user = userEvent.setup()
        val view = renderAdminProfileCycleView(startWithOtherProfile = true)

        waitForCondition { view.container.querySelector(".profile-dialog") != null }
        connectedAdmin.complete(ContentCapabilitiesResult(isAdmin = true))
        waitForCondition { view.container.querySelector(".connected-shell__avatar-badge") != null }

        user.click(showAdminProfileButton(view.container)).await()
        displayedAdmin.complete(ContentCapabilitiesResult(isAdmin = true))
        waitForCondition { view.container.querySelector(".profile-dialog__identity-badge") != null }

        otherProfile.complete(ContentCapabilitiesResult(isAdmin = false))
        delay(100)
        assertEquals(
            text("identity.admin.badge"),
            view.container.querySelector(".profile-dialog__identity-badge")?.textContent?.trim(),
        )
    }

    @Test
    fun `updates connected header badge when switching connected profile to non admin`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                capabilitiesByGid =
                    mapOf(
                        identityForTests.gid to ContentCapabilitiesResult(isAdmin = true),
                        "gid-other" to ContentCapabilitiesResult(isAdmin = false),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        sessionStore.prime(IdentitySessionSnapshot(identityForTests.gid, false))
        val user = userEvent.setup()
        val view = renderConnectedAdminSwitchView()

        waitForCondition { view.container.querySelector(".connected-shell__avatar-badge") != null }
        user.click(switchConnectedProfileButton(view.container)).await()
        waitForCondition { view.container.querySelector(".connected-shell__avatar-badge") == null }
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
    fun `closes inbox when clicking the inbox button again`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                inboxResponses =
                    listOf(
                        AdminMessageListResult(
                            listOf(AdminMessage("message-1", adminSenderGid, 10, "First subject", "First body")),
                        ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }

        user.click(inboxButton(view.container)).await()
        waitForCondition { podAccess.messagesRequests.size == 1 }
        waitForCondition { view.container.textContent?.contains("First subject") == true }
        waitForCondition { inboxBadge(view.container) == null }

        user.click(inboxButton(view.container)).await()
        waitForCondition { view.container.querySelector(".message-inbox") == null }
        assertEquals(1, podAccess.messagesRequests.size)
    }

    @Test
    fun `hides unread badge after inbox messages were seen`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                inboxResponses =
                    listOf(
                        AdminMessageListResult(
                            listOf(
                                AdminMessage("message-1", adminSenderGid, 10, "First subject", "First body"),
                                AdminMessage("message-2", adminSenderGid, 11, "Second subject", "Second body"),
                            ),
                        ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        inboxStateStore.store(
            "https://pod.example",
            identityForTests.gid,
            AdminMessageInboxStateSnapshot(
                knownMessageIds = listOf("message-1", "message-2"),
                seenMessageIds = emptyList(),
            ),
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { unreadBadgeCount(view.container) == "2" }
        user.click(inboxButton(view.container)).await()
        waitForCondition { view.container.textContent?.contains("First subject") == true }
        waitForCondition { inboxBadge(view.container) == null }
        assertEquals(
            listOf("message-1", "message-2"),
            inboxStateStore.load("https://pod.example", identityForTests.gid)?.seenMessageIds,
        )
    }

    @Test
    fun `reloads inbox messages when connected profile changes`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                inboxResponses =
                    listOf(
                        AdminMessageListResult(
                            listOf(AdminMessage("message-1", adminSenderGid, 10, "Second profile subject", "Second body")),
                        ),
                        AdminMessageListResult(
                            listOf(AdminMessage("message-2", adminSenderGid, 11, "Second profile refreshed", "Second body refreshed")),
                        ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedAdminSwitchView()

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        user.click(switchConnectedProfileButton(view.container)).await()

        waitForCondition { podAccess.messagesRequests.size == 1 }
        user.click(inboxButton(view.container)).await()
        waitForCondition { podAccess.messagesRequests.size == 2 }
        waitForCondition { view.container.textContent?.contains("Second profile refreshed") == true }
    }

    @Test
    fun `closes inbox when clicking outside`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                inboxResponses =
                    listOf(
                        AdminMessageListResult(
                            listOf(AdminMessage("message-1", adminSenderGid, 10, "First subject", "First body")),
                        ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        user.click(inboxButton(view.container)).await()
        waitForCondition { view.container.querySelector(".message-inbox") != null }

        user.click(view.container.querySelector(".post-card") ?: error("Post card missing")).await()
        waitForCondition { view.container.querySelector(".message-inbox") == null }
    }

    @Test
    fun `shows admin notice sender with avatar and fictional name`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess =
            RecordingPodAccess(
                responses = mapOf("Alpha" to queryResult("p1")),
                inboxResponses =
                    listOf(
                        AdminMessageListResult(
                            listOf(AdminMessage("message-1", adminSenderGid, 10, "First subject", "First body")),
                        ),
                    ),
            )
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        val user = userEvent.setup()
        val view = renderConnectedView()
        val senderVisuals = IdentityVisualsFactory(FictionalNameGenerator(), DiceBearAvatarGenerator()).createFromGid(adminSenderGid)
        val expectedSenderName = senderVisuals.name.toString().trim()
        val expectedSenderLabel =
            ConnectedViewTestInjector.get<Translator>().translate(
                "connected.messages.inbox.from",
                mapOf("name" to expectedSenderName),
            )

        waitForCondition { view.container.querySelectorAll(".post-card").length == 1 }
        user.click(inboxButton(view.container)).await()

        waitForCondition { view.container.querySelector(".message-inbox__sender-name") != null }
        assertEquals(
            expectedSenderLabel,
            view.container.querySelector(".message-inbox__sender-name")?.textContent?.trim(),
        )
        assertEquals(
            senderVisuals.avatar,
            (view.container.querySelector(".message-inbox__sender-avatar") as? HTMLImageElement)?.getAttribute("src"),
        )
        assertTrue(view.container.textContent?.contains(adminSenderGid) != true)
    }

    @Test
    fun `shows unread badge from local inbox state before pod reports messages`() = runTest {
        window.asDynamic().MOSA_POD_BASE = "https://pod.example"
        val podAccess = RecordingPodAccess(responses = mapOf("Alpha" to queryResult("p1")))
        overrideDependencies(
            topics = listOf(Topic("Alpha")),
            podAccess = podAccess,
        )
        inboxStateStore.store(
            "https://pod.example",
            identityForTests.gid,
            AdminMessageInboxStateSnapshot(
                knownMessageIds = listOf("message-1", "message-2", "message-3"),
                seenMessageIds = listOf("message-1"),
            ),
        )

        val view = renderConnectedView()

        waitForCondition { unreadBadgeCount(view.container) == "2" }
        assertTrue(podAccess.messagesRequests.isEmpty())
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

    private fun renderRefreshableConnectedView(): RenderResult =
        render(
            RefreshableConnectedViewHarness.create(),
        )

    private fun renderAdminProfileCycleView(startWithOtherProfile: Boolean = false): RenderResult =
        render(
            AdminProfileCycleHarness.create {
                this.startWithOtherProfile = startWithOtherProfile
            },
        )

    private fun renderConnectedAdminSwitchView(): RenderResult =
        render(
            ConnectedAdminSwitchHarness.create(),
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
                single<AdminMessageInboxStateStore> { InMemoryAdminMessageInboxStateStore() }
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
                single {
                    val subtle = js("{}").unsafeCast<dynamic>()
                    subtle.sign = { _: String, _: dynamic, bytes: ByteArray ->
                        kotlin.js.Promise.resolve(bytes.toArrayBuffer())
                    }
                    subtle.digest = { _: String, bytes: ByteArray ->
                        kotlin.js.Promise.resolve(bytes.toArrayBuffer())
                    }
                    subtle.importKey = { _: String, keyData: ByteArray, _: dynamic, _: Boolean, _: Array<String> ->
                        val key = js("{}").unsafeCast<dynamic>()
                        key.raw = keyData
                        kotlin.js.Promise.resolve<dynamic>(key)
                    }
                    subtle.verify = { _: String, _: dynamic, _: ByteArray, _: ByteArray ->
                        kotlin.js.Promise.resolve(true)
                    }
                    SubtleCrypto(subtle)
                }
                single { ContentSigner(get()) }
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
                single { PostPublisher(get(), get()) }
                single { AdminMessageInboxService(get()) }
                single { AdminMessagePublisher(get()) }
                single { ContentCapabilitiesService(get()) }
                single { PostDeletionService(get(), get(), get()) }
                single { AllPostsDeletionService(get(), get(), get()) }
                single { ReplyPublisher(get(), get()) }
                single { ContentSignatureVerifier(get()) }
                single { TopicSearchService(get(), get(), get(), get()) }
                single { ReplySearchService(get(), get(), get(), get()) }
            }
                startKoin { modules(module) }
        sessionStore = ConnectedViewTestInjector.get<IdentitySessionStore>() as ConnectedViewIdentitySessionStore
        sessionStore.prime(IdentitySessionSnapshot(null, false))
        inboxStateStore = ConnectedViewTestInjector.get<AdminMessageInboxStateStore>() as InMemoryAdminMessageInboxStateStore
        inboxStateStore.clear()
    }

    private fun avatarButton(container: Element): Element =
        container.querySelector(".connected-shell__avatar-button")
            ?: error("Avatar button missing")

    private fun composerButton(container: Element): Element =
        container.querySelector(".composer-button") ?: error("Composer button missing")

    private fun inboxButton(container: Element): Element =
        container.querySelector(".connected-shell__inbox-button") ?: error("Inbox button missing")

    private fun inboxBadge(container: Element): Element? =
        container.querySelector(".connected-shell__inbox-badge")

    private fun unreadBadgeCount(container: Element): String? =
        inboxBadge(container)?.textContent?.trim()

    private fun composerTextarea(container: Element): Element =
        container.querySelector(".composer-dialog__textarea") ?: error("Composer textarea missing")

    private fun sendComposerButton(container: Element): Element =
        container.querySelector(".composer-dialog__send") ?: error("Composer send button missing")

    private fun snapshotRefreshButton(container: Element): Element =
        container.querySelector(".snapshot-refresh-trigger") ?: error("Snapshot refresh trigger missing")

    private fun showAdminProfileButton(container: Element): Element =
        container.querySelector(".admin-profile-trigger") ?: error("Admin profile trigger missing")

    private fun showOtherProfileButton(container: Element): Element =
        container.querySelector(".other-profile-trigger") ?: error("Other profile trigger missing")

    private fun switchConnectedProfileButton(container: Element): Element =
        container.querySelector(".connected-profile-switch-trigger") ?: error("Connected profile switch trigger missing")

    private fun text(key: String): String = ConnectedViewTestInjector.get<Translator>().translate(key)
}

private fun ByteArray.toArrayBuffer(): ArrayBuffer =
    ArrayBuffer(size).also { Int8Array(it).set(unsafeCast<Int8Array>()) }

private val ConnectedViewHarness = FC<Props> {
    IdentityConnectedView {
        identityName = "Tester"
        profile = placeholderProfile()
    }
}

private val RefreshableConnectedViewHarness = FC<Props> {
    val (snapshotRefreshVersion, setSnapshotRefreshVersion) = useState(0)
    button {
        className = web.cssom.ClassName("snapshot-refresh-trigger")
        onClick = { setSnapshotRefreshVersion { current -> current + 1 } }
    }
    IdentityConnectedView {
        identityName = "Tester"
        profile = placeholderProfile(snapshotRefreshVersion = snapshotRefreshVersion)
    }
}

private external interface AdminProfileCycleHarnessProps : Props {
    var startWithOtherProfile: Boolean
}

private val AdminProfileCycleHarness = FC<AdminProfileCycleHarnessProps> { props ->
    val (currentProfile, setProfile) =
        useState(
            if (props.startWithOtherProfile) {
                placeholderProfile(identityGid = "gid-other", connectedIdentityGid = identityForTests.gid)
            } else {
                placeholderProfile()
            },
        )
    button {
        className = web.cssom.ClassName("admin-profile-trigger")
        onClick = { setProfile { placeholderProfile() } }
    }
    button {
        className = web.cssom.ClassName("other-profile-trigger")
        onClick = { setProfile { placeholderProfile(identityGid = "gid-other", connectedIdentityGid = identityForTests.gid) } }
    }
    IdentityConnectedView {
        identityName = "Tester"
        profile = currentProfile
    }
}

private val ConnectedAdminSwitchHarness = FC<Props> {
    val (currentProfile, setProfile) =
        useState(
            placeholderProfile(
                identityGid = identityForTests.gid,
                connectedIdentityGid = identityForTests.gid,
                canDeletePosts = true,
            ),
        )
    button {
        className = web.cssom.ClassName("connected-profile-switch-trigger")
        onClick = {
            setProfile {
                placeholderProfile(
                    identityGid = "gid-other",
                    connectedIdentityGid = "gid-other",
                    canDeletePosts = true,
                )
            }
        }
    }
    IdentityConnectedView {
        identityName = "Tester"
        profile = currentProfile
    }
}

private object ConnectedViewTestInjector : KoinComponent

private fun placeholderProfile(
    snapshotRefreshVersion: Int = 0,
    identityGid: String = identityForTests.gid,
    connectedIdentityGid: String? = identityGid,
    canDeletePosts: Boolean = false,
): IdentityScreen.Profile =
    IdentityScreen.Profile(
        identityGid = identityGid,
        connectedIdentityGid = connectedIdentityGid,
        identityNameParts = FictionalName(emptyList(), ""),
        identityAvatar = null,
        isAdmin = false,
        isBusy = false,
        errorText = null,
        statusText = null,
        snapshotRefreshVersion = snapshotRefreshVersion,
        canDelete = false,
        canDeletePosts = canDeletePosts,
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
        onCopyGid = {},
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

private val adminSenderGid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).toBase64Url()

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

private class InMemoryAdminMessageInboxStateStore : AdminMessageInboxStateStore {
    private val snapshots = mutableMapOf<Pair<String, String>, AdminMessageInboxStateSnapshot>()

    override fun load(
        podBase: String,
        gid: String,
    ): AdminMessageInboxStateSnapshot? = snapshots[podBase to gid]

    override fun store(
        podBase: String,
        gid: String,
        snapshot: AdminMessageInboxStateSnapshot,
    ) {
        snapshots[podBase to gid] = snapshot
    }

    fun clear() {
        snapshots.clear()
    }
}

private class RecordingPodAccess(
    responses: Map<String, QueryResult>,
    private val additionalResponses: Map<String, List<QueryResult>> = emptyMap(),
    replyResponses: Map<String, List<QueryResult>> = emptyMap(),
    private val inboxResponses: List<AdminMessageListResult> = listOf(AdminMessageListResult(emptyList())),
    private val capabilities: ContentCapabilitiesResult = ContentCapabilitiesResult(isAdmin = false),
    private val capabilitiesByGid: Map<String, ContentCapabilitiesResult> = emptyMap(),
    private val deferredCapabilitiesByGid: Map<String, MutableList<CompletableDeferred<ContentCapabilitiesResult>>> = emptyMap(),
) : PodAccess {
    val approveInvocations = mutableListOf<String>()
    val searchInvocations = mutableListOf<QueryRequest>()
    val queryInvocations get() = searchInvocations
    val publishRequests = mutableListOf<straightway.mosa.content.PublishRequest>()
    val replyRequests = mutableListOf<straightway.mosa.content.ReplyRequest>()
    val messagesRequests = mutableListOf<String>()
    var shouldFailPublish = false
    private val inboxResponseQueue = inboxResponses.toMutableList()
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

    override suspend fun sendAdminMessage(
        request: CreateAdminMessageRequest,
        jwt: String,
    ) {
        error("Not implemented")
    }

    override suspend fun messages(jwt: String): AdminMessageListResult {
        messagesRequests += jwt
        return inboxResponseQueue.removeFirstOrNull() ?: AdminMessageListResult(emptyList())
    }

    override suspend fun exportPostsCsv(jwt: String): ByteArray {
        error("Not implemented")
    }

    override suspend fun contentCapabilities(
        jwt: String,
        gid: String?,
    ): ContentCapabilitiesResult {
        val deferred =
            gid?.let { requestedGid ->
                deferredCapabilitiesByGid[requestedGid]?.let { queue ->
                    if (queue.isEmpty()) {
                        null
                    } else {
                        queue.removeAt(0)
                    }
                }
            }
        if (deferred != null) {
            return deferred.await()
        }
        return gid?.let { capabilitiesByGid[it] } ?: capabilities
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
                val publicKeyBytes = "pub:$id".encodeToByteArray()
                FetchResult.Entry(
                    id = entry.id,
                    content = entry.content,
                    authorGID = publicKeyBytes.toBase64Url(),
                    storedAt = entry.storedAt,
                    rootId = entry.rootId,
                    authorPublicKey = publicKeyBytes.toBase64Url(),
                    authorSignature = "sig:$id".encodeToByteArray().toBase64Url(),
                    signatureVersion = 1,
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

    override suspend fun termsBaselineClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsDefaultClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsMosaClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsOperatorClearTerms(): String {
        error("Not implemented")
    }

    override suspend fun termsServiceClearTerms(): String {
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

    override suspend fun clearAll() {
        snapshots.clear()
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

    override suspend fun clearAll() {
        snapshots.clear()
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

private fun queryResult(
    id: String,
    authorGid: String = "author",
): QueryResult =
    QueryResult(
        entries =
            listOf(
                QueryResult.Entry(id, "content-$id", 0.9f, authorGid, 0),
            ),
        queryOnId = "query-on-$id",
    )

private fun QueryResult.toSearchResult(): SearchResult =
    SearchResult(
        entries = entries.map { SearchResult.Entry(it.id, it.similarity) },
        queryOnId = queryOnId,
    )
