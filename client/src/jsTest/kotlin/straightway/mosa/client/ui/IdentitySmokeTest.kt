package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.koin.dsl.module
import org.koin.core.context.GlobalContext
import org.w3c.dom.Element
import react.FC
import react.Props
import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityProvider
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.IdentityStore
import straightway.mosa.client.identity.IdentityStoreState
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent
import straightway.mosa.client.util.Base64.toBase64Url

class IdentitySmokeTest {
    private lateinit var identityStore: InMemoryIdentityStore
    private lateinit var identityProvider: QueueIdentityProvider
    private lateinit var repository: IdentityRepository
    private var originalFactory: IdentitySessionStateMachineFactory? = null
    private lateinit var sessionStore: SmokeIdentitySessionStore
    private lateinit var overrideModule: org.koin.core.module.Module

    @BeforeTest
    fun setUp() {
        AppModule.start()
        sessionStore = SmokeIdentitySessionStore()
        overrideModule =
            module {
                single<IdentitySessionStore> { sessionStore }
            }
        GlobalContext.get().loadModules(listOf(overrideModule), allowOverride = true)
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
        repository = IdentityRepository(identityStore, identityProvider)
        originalFactory = identitySessionStateMachineFactory
        identitySessionStateMachineFactory =
            { config ->
                IdentitySessionStateMachine(
                    repository = repository,
                    transfer = config.transfer,
                    textProvider = config.textProvider,
                    visualsProvider = { identity -> config.visualsFactory.create(identity) },
                    scope = config.scope,
                    logger = config.logger,
                    sessionStore = config.sessionStore,
                )
            }
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        if (this::overrideModule.isInitialized) {
            GlobalContext.get().unloadModules(listOf(overrideModule))
        }
        originalFactory?.let { identitySessionStateMachineFactory = it }
    }

    @Test
    fun `runs identity lifecycle from profile to topic board`() = runTest {
        val result = renderSmokeApp()
        waitForIdentityReady(result.container)
        val user = userEvent.setup()

        val initialIdentity = profileSnapshot(result.container)

        waitForButtonEnabled("next button") { nextButton(result.container) }
        user.click(nextButton(result.container)).await()
        waitForProfileChange(result.container) { it.name != initialIdentity.name }
        val createdIdentity = profileSnapshot(result.container)
        assertNotEquals(initialIdentity.avatar, createdIdentity.avatar)

        user.click(toolsToggle(result.container)).await()
        waitForElement("delete button") { findDeleteButton(result.container) }
        user.click(deleteButton(result.container)).await()
        waitForProfileChange(result.container) { it.name == initialIdentity.name }

        waitForButtonEnabled("next button after delete") { nextButton(result.container) }
        user.click(nextButton(result.container)).await()
        waitForProfileChange(result.container) { it.name != initialIdentity.name }
        val navigatedIdentity = profileSnapshot(result.container)
        assertNotEquals(createdIdentity.name, navigatedIdentity.name)
        waitForButtonEnabled("previous button") { previousButton(result.container) }
        user.click(previousButton(result.container)).await()
        waitForProfileChange(result.container) { it.name == initialIdentity.name }

        waitForButtonEnabled("connect button") { connectButton(result.container) }
        user.click(connectButton(result.container)).await()
        waitForConnectedView(result.container)
    }

    private suspend fun waitForIdentityReady(container: Element) {
        eventually("initial identity visuals") {
            check(container.querySelector(".profile-dialog__identity-avatar") != null)
        }
    }

    private suspend fun waitForProfileChange(
        container: Element,
        predicate: (IdentitySnapshot) -> Boolean,
    ) {
        eventually("profile view") {
            val snapshot = profileSnapshot(container)
            check(predicate(snapshot))
        }
    }

    private suspend fun waitForConnectedView(container: Element) {
        eventually("connected view") {
            val board = container.querySelector(".topic-board") ?: error("Topic board missing")
            check(board.querySelectorAll(".topic-board__column").length == 3)
            val addButton = board.querySelector(".topic-board__add-button")
            check(addButton != null)
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

    private fun profileSnapshot(container: Element): IdentitySnapshot {
        val value =
            container.querySelector(".profile-dialog__identity-value")
                ?: error("Identity value element missing")
        val text =
            value.textContent.orEmpty()
                .replace("\\s+".toRegex(), " ")
                .trim()
        val avatar = container.querySelector(".profile-dialog__identity-avatar")?.getAttribute("src")
        return IdentitySnapshot(text, avatar)
    }

    private fun renderSmokeApp(): RenderResult =
        render(
            IdentitySmokeApp.create(),
        )

    private fun nextButton(container: Element): Element =
        (container.querySelectorAll(".profile-dialog__nav-button").item(1) as? Element)
            ?: error("Next button missing")

    private fun previousButton(container: Element): Element =
        (container.querySelectorAll(".profile-dialog__nav-button").item(0) as? Element)
            ?: error("Previous button missing")

    private fun connectButton(container: Element): Element =
        container.querySelector(".profile-dialog__actions button")
            ?: error("Connect button missing")

    private fun toolsToggle(container: Element): Element =
        container.querySelector(".profile-dialog__identity-tools-trigger")
            ?: error("Tools toggle missing")

    private fun deleteButton(container: Element): Element =
        (findDeleteButton(container)
            ?: error("Delete button missing"))

    private fun findDeleteButton(container: Element): Element? =
        container.querySelector(".profile-dialog__identity-tool .fa-trash-can")
            ?.parentElement

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

}

private data class IdentitySnapshot(
    val name: String,
    val avatar: String?,
)

private val IdentitySmokeApp = FC<Props> {
    when (val screen = useIdentitySession()) {
        IdentityScreen.Loading -> Unit
        is IdentityScreen.Connected ->
            IdentityConnectedView {
                identityName = screen.identityName
                profile = screen.profile
            }

        is IdentityScreen.Profile ->
            IdentityProfileView {
                applyFrom(screen)
            }
    }
}

private class SmokeIdentitySessionStore : IdentitySessionStore {
    private var snapshot: IdentitySessionSnapshot? = null

    override suspend fun load(): IdentitySessionSnapshot? = snapshot

    override suspend fun save(snapshot: IdentitySessionSnapshot) {
        this.snapshot = snapshot
    }
}

private class InMemoryIdentityStore : IdentityStore {
    private var state: IdentityStoreState? = null

    override suspend fun loadState(): IdentityStoreState? = state

    override suspend fun storeState(state: IdentityStoreState) {
        this.state = state
    }
}

private class QueueIdentityProvider(
    identities: List<Identity>,
) : IdentityProvider {
    private val source = identities.toList()
    private val queue = ArrayDeque(source)

    override suspend fun generate(): Identity =
        queue.removeFirstOrNull() ?: source.last()
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
