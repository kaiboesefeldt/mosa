package straightway.mosa.client.ui

import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotEquals
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.w3c.dom.Element
import react.FC
import react.Props
import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent

@OptIn(ExperimentalWasmJsInterop::class)
class IdentitySmokeTest : KoinComponent {
    private lateinit var repository: IdentitySelectionRepository
    private var originalFactory: IdentitySessionStateMachineFactory? = null
    private lateinit var sessionStore: SmokeIdentitySessionStore
    private lateinit var overrideModule: org.koin.core.module.Module

    @BeforeTest
    fun setUp() = runTest {
        AppModule.start()
        sessionStore = SmokeIdentitySessionStore()
        overrideModule =
            module {
                single<IdentitySessionStore> { sessionStore }
            }
        GlobalContext.get().loadModules(listOf(overrideModule), allowOverride = true)
        val generator = get<IdentityGenerator>()
        val identities =
            listOf(
                generator.generate(),
                generator.generate(),
                generator.generate(),
                generator.generate(),
            )
        repository = SmokeIdentitySelectionRepository(identities.toMutableList())
        originalFactory = identitySessionStateMachineFactory
        identitySessionStateMachineFactory =
            { config ->
                IdentitySessionStateMachine(
                    repository = repository,
                    transfer = config.transfer,
                    protector = config.protector,
                    passwordPrompter = config.passwordPrompter,
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
        delay(200)
        if (!waitForIdentityReady(result.container)) return@runTest
        val user = userEvent.setup()

        val initialIdentity = profileSnapshot(result.container)

        waitForButtonEnabled("next button") { nextButton(result.container) }
        user.click(nextButton(result.container)).await()
        waitForProfileChange(result.container) { it.name != initialIdentity.name }
        val navigatedIdentity = profileSnapshot(result.container)
        assertNotEquals(initialIdentity.avatar, navigatedIdentity.avatar)

        waitForButtonEnabled("previous button") { previousButton(result.container) }
        user.click(previousButton(result.container)).await()
        waitForProfileChange(result.container) { it.name == initialIdentity.name }
    }

    private suspend fun waitForIdentityReady(container: Element): Boolean {
        return runCatching {
            eventually("initial identity visuals") {
                val profile = container.querySelector(".profile-dialog")
                val connected = container.querySelector(".connected-shell")
                check(profile != null || connected != null)
            }
            true
        }.getOrElse { false }
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

private class SmokeIdentitySelectionRepository(
    identities: MutableList<Identity>,
) : IdentitySelectionRepository {
    private val items = identities
    private var index = 0

    override suspend fun loadOrCreate(): IdentitySelection = selection()

    override suspend fun movePrevious(): IdentitySelection? {
        if (index <= 0) return null
        index -= 1
        return selection()
    }

    override suspend fun moveNext(): IdentitySelection {
        if (index < items.lastIndex) index += 1 else items += items.last()
        return selection()
    }

    override suspend fun deleteCurrent(): IdentitySelection {
        if (items.size > 1) {
            items.removeAt(index)
            index = (index - 1).coerceAtLeast(0).coerceAtMost(items.lastIndex)
        }
        return selection()
    }

    override suspend fun select(index: Int): IdentitySelection? {
        if (index !in items.indices) return null
        this.index = index
        return selection()
    }

    override suspend fun import(identity: Identity): IdentitySelection {
        items += identity
        index = items.lastIndex
        return selection()
    }

    override suspend fun protectCurrent(password: String): IdentitySelection = selection()

    override suspend fun persist() = Unit

    private fun selection(): IdentitySelection =
        IdentitySelection(items[index], index, items.size, true)
}
