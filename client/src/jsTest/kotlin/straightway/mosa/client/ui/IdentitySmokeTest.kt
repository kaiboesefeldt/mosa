package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Element
import react.FC
import react.Props
import react.create
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.AppModule
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityProvider
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.IdentityStore
import straightway.mosa.client.identity.IdentityStoreState
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.ReplyRequest

class IdentitySmokeTest {
    private lateinit var identityStore: InMemoryIdentityStore
    private lateinit var identityProvider: QueueIdentityProvider
    private lateinit var podAccess: RecordingPodAccess
    private lateinit var repository: IdentityRepository
    private var originalFactory: IdentitySessionStateMachineFactory? = null

    @BeforeTest
    fun setUp() {
        AppModule.start()
        identityStore = InMemoryIdentityStore()
        identityProvider =
            QueueIdentityProvider(
                listOf(
                    testIdentity("alpha"),
                    testIdentity("beta"),
                    testIdentity("gamma"),
                    testIdentity("delta"),
                ),
            )
        podAccess = RecordingPodAccess()
        repository = IdentityRepository(identityStore, identityProvider)
        originalFactory = identitySessionStateMachineFactory
        identitySessionStateMachineFactory =
            { config ->
                IdentitySessionStateMachine(
                    repository = repository,
                    transfer = config.transfer,
                    misJwtBuilder = { identity, cert -> "jwt:${identity.gid}:${cert.gid}" },
                    podAccessFactory = { podAccess },
                    textProvider = config.textProvider,
                    visualsProvider = { identity -> config.visualsFactory.create(identity) },
                    defaultPod = config.defaultPod,
                    scope = config.scope,
                    logger = config.logger,
                )
            }
        window.asDynamic().MOSA_POD_BASE = "https://pod.smoke.test"
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        originalFactory?.let { identitySessionStateMachineFactory = it }
        window.asDynamic().MOSA_POD_BASE = js("undefined")
    }

    @Test
    fun `runs identity lifecycle from landing to pod connection`() = runTest {
        val result = renderSmokeApp()
        waitForIdentityReady(result.container)
        val user = userEvent.setup()

        val initialIdentity = landingSnapshot(result.container)

        waitForButtonEnabled("next button") { nextButton(result.container) }
        user.click(nextButton(result.container)).await()
        waitForLandingChange(result.container) { it.name != initialIdentity.name }
        val createdIdentity = landingSnapshot(result.container)
        assertNotEquals(initialIdentity.avatar, createdIdentity.avatar)

        user.click(toolsToggle(result.container)).await()
        waitForElement("delete button") { findDeleteButton(result.container) }
        user.click(deleteButton(result.container)).await()
        waitForLandingChange(result.container) { it.name == initialIdentity.name }

        waitForButtonEnabled("next button after delete") { nextButton(result.container) }
        user.click(nextButton(result.container)).await()
        waitForLandingChange(result.container) { it.name != initialIdentity.name }
        val navigatedIdentity = landingSnapshot(result.container)
        assertNotEquals(createdIdentity.name, navigatedIdentity.name)
        waitForButtonEnabled("previous button") { previousButton(result.container) }
        user.click(previousButton(result.container)).await()
        waitForLandingChange(result.container) { it.name == initialIdentity.name }

        waitForButtonEnabled("connect button") { connectButton(result.container) }
        user.click(connectButton(result.container)).await()
        waitForPodConnection()
        waitForConnectedView(result.container)

        val activeGid = identityStore.currentIdentity()!!.gid
        assertEquals(listOf(activeGid), podAccess.approvedGids)
        assertEquals(listOf("Anything"), podAccess.queryTopics)
        assertEquals("jwt:$activeGid:$activeGid", podAccess.lastJwt)
    }

    private suspend fun waitForIdentityReady(container: Element) {
        eventually("initial identity visuals") {
            check(container.querySelector(".landing-dialog__identity-avatar") != null)
        }
    }

    private suspend fun waitForLandingChange(
        container: Element,
        predicate: (IdentitySnapshot) -> Boolean,
    ) {
        eventually("landing view") {
            val snapshot = landingSnapshot(container)
            check(predicate(snapshot))
        }
    }

    private suspend fun waitForConnectedView(container: Element) {
        eventually("connected view") {
            val shell = container.querySelector(".connected-shell") ?: error("Connected view missing")
            val header = shell.querySelector(".connected-shell__header")?.textContent.orEmpty()
            check(header.isNotBlank())
            check(shell.querySelectorAll(".connected-shell__section-title").length >= 1)
        }
    }

    private suspend fun eventually(description: String = "condition", assertion: () -> Unit) {
        repeat(400) {
            runCatching {
                assertion()
                return
            }.onFailure {
                delay(25)
            }
        }
        error("Condition was not met: $description")
    }

    private fun landingSnapshot(container: Element): IdentitySnapshot {
        val value =
            container.querySelector(".landing-dialog__identity-value")
                ?: error("Identity value element missing")
        val text =
            value.textContent.orEmpty()
                .replace("\\s+".toRegex(), " ")
                .trim()
        val avatar = container.querySelector(".landing-dialog__identity-avatar")?.getAttribute("src")
        return IdentitySnapshot(text, avatar)
    }

    private fun renderSmokeApp(): RenderResult =
        render(
            IdentitySmokeApp.create(),
        )

    private fun nextButton(container: Element): Element =
        (container.querySelectorAll(".landing-dialog__nav-button").item(1) as? Element)
            ?: error("Next button missing")

    private fun previousButton(container: Element): Element =
        (container.querySelectorAll(".landing-dialog__nav-button").item(0) as? Element)
            ?: error("Previous button missing")

    private fun connectButton(container: Element): Element =
        container.querySelector(".landing-dialog__actions button")
            ?: error("Connect button missing")

    private fun toolsToggle(container: Element): Element =
        container.querySelector(".landing-dialog__identity-tools-trigger")
            ?: error("Tools toggle missing")

    private fun deleteButton(container: Element): Element =
        (findDeleteButton(container)
            ?: error("Delete button missing"))

    private fun findDeleteButton(container: Element): Element? =
        (container.querySelector(".landing-dialog__identity-tool .fa-trash-can")
            ?.parentElement as? Element)

    private suspend fun waitForButtonEnabled(
        description: String,
        lookup: () -> Element,
    ) {
        eventually(description) {
            val button = lookup()
            val disabled = button.asDynamic().disabled as? Boolean ?: false
            check(!disabled)
        }
    }

    private suspend fun waitForElement(
        description: String,
        lookup: () -> Element?,
    ) {
        eventually(description) { checkNotNull(lookup()) }
    }

    private suspend fun waitForPodConnection() {
        eventually("pod approval") { check(podAccess.approvedGids.isNotEmpty()) }
    }
}

private data class IdentitySnapshot(
    val name: String,
    val avatar: String?,
)

private val IdentitySmokeApp = FC<Props> {
    when (val screen = useIdentitySession()) {
        is IdentityScreen.Connected ->
            IdentityConnectedView {
                identityName = screen.identityName
                misJwt = screen.misJwt
                queryPreview = screen.queryPreview
            }

        is IdentityScreen.Landing ->
            IdentityLandingView {
                identityNameParts = screen.identityNameParts
                identityAvatar = screen.identityAvatar
                isBusy = screen.isBusy
                errorText = screen.errorText
                canDelete = screen.canDelete
                canNavigatePrevious = screen.canNavigatePrevious
                canNavigateNext = screen.canNavigateNext
                isShowingLastIdentity = screen.isShowingLastIdentity
                identityIndex = screen.identityIndex
                identityTotal = screen.identityTotal
                canConnect = screen.canConnect
                canDownload = screen.canDownload
                canUpload = screen.canUpload
                onConnect = screen.onConnect
                onPrevious = screen.onPrevious
                onNext = screen.onNext
                onDelete = screen.onDelete
                onSelect = screen.onSelect
                onDownload = screen.onDownload
                onUpload = screen.onUpload
            }
    }
}

private class InMemoryIdentityStore : IdentityStore {
    private var state: IdentityStoreState? = null

    override suspend fun loadState(): IdentityStoreState? = state

    override suspend fun storeState(state: IdentityStoreState) {
        this.state = state
    }

    fun currentIdentity(): Identity? = state?.current()
}

private class QueueIdentityProvider(
    identities: List<Identity>,
) : IdentityProvider {
    private val source = identities.toList()
    private val queue = ArrayDeque(source)

    override suspend fun generate(): Identity =
        queue.removeFirstOrNull() ?: source.last()
}

private class RecordingPodAccess : PodAccess {
    val approvedGids = mutableListOf<String>()
    val queryTopics = mutableListOf<String>()
    var lastJwt: String? = null

    override suspend fun approveGid(gid: String): PodCertificate {
        approvedGids += gid
        return PodCertificate(gid, 1_234, "sig-$gid")
    }

    override suspend fun publish(request: PublishRequest, jwt: String) = Unit

    override suspend fun reply(request: ReplyRequest, jwt: String) = Unit

    override suspend fun query(request: QueryRequest, jwt: String): QueryResult {
        queryTopics += request.topic.orEmpty()
        lastJwt = jwt
        return QueryResult(
            entries =
                listOf(
                    QueryResult.Entry(
                        id = "preview-id",
                        content = "Prototype",
                        similarity = 0.9f,
                        authorGID = "author",
                        storedAt = 42,
                        rootId = null,
                    ),
                ),
            queryOnId = "preview",
        )
    }

    override suspend fun queryOn(request: QueryOnRequest, jwt: String): QueryResult =
        QueryResult(emptyList(), "preview")
}

private fun testIdentity(label: String): Identity {
    val gid = repeatedBytes(label).toBase64Url()
    val keyPair = js("{}")
    keyPair.publicKey = js("{}")
    keyPair.privateKey = js("{}")
    return Identity(gid, keyPair.unsafeCast<CryptoKeyPair>())
}

private fun repeatedBytes(label: String): ByteArray {
    val chars = label.encodeToByteArray()
    val buffer = ByteArray(32)
    buffer.indices.forEach { index ->
        buffer[index] = chars[index % chars.size]
    }
    return buffer
}
