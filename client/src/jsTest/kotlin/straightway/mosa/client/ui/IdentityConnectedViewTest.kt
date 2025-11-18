package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.w3c.dom.Element
import react.FC
import react.Props
import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent
import straightway.mosa.identity.FictionalName

class IdentityConnectedViewTest {
    private var originalPrompt: dynamic = null
    private lateinit var sessionStore: IdentitySessionStore

    @BeforeTest
    fun setUp() {
        AppModule.start()
        originalPrompt = window.asDynamic().prompt
        sessionStore = ConnectedViewTestInjector.get()
        sessionStore.save(IdentitySessionSnapshot(null, false))
    }

    @AfterTest
    fun tearDown() {
        cleanup()
        window.asDynamic().prompt = originalPrompt
        sessionStore.save(IdentitySessionSnapshot(null, false))
    }

    @Test
    fun `adds topic via prompt`() = runTest {
        window.asDynamic().prompt = { _: String -> "Neues Thema" }
        val user = userEvent.setup()
        val view = renderConnectedView()

        waitForCondition { view.container.querySelector(".topic-board__add-button") != null }
        val button = addButton(view.container)
        user.click(button).await()
        waitForCondition { view.container.querySelectorAll(".topic-card").length == 1 }
        val cardText = view.container.querySelector(".topic-card")?.textContent?.trim()
        assertEquals("Neues Thema", cardText)
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
