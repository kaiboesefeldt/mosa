package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.get
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLElement
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.fireEvent
import straightway.mosa.client.testing.screen
import straightway.mosa.identity.FictionalName

class IdentityCarouselTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `renders provided identity avatar`() = runTest {
        val avatarUri = "data:image/png;base64,avatar"
        val result =
            renderIdentityProfileView {
                identityAvatar = avatarUri
            }

        val avatar = result.container.querySelector("img.profile-dialog__identity-avatar") as HTMLImageElement

        assertEquals(avatarUri, avatar.getAttribute("src"))
        assertEquals("Default Identity", avatar.getAttribute("alt"))
    }

    @Test
    fun `renders blank identity line when name parts missing`() = runTest {
        val result =
            renderIdentityProfileView {
                identityNameParts = FictionalName()
            }

        val valueElement = result.container.querySelector(".profile-dialog__identity-value")!!

        assertEquals("", valueElement.textContent.orEmpty())
    }

    @Test
    fun `formats multi line identity name`() = runTest {
        val result =
            renderIdentityProfileView {
                identityNameParts = FictionalName(
                    givenNames = listOf("Ada", "M."),
                    sureName = "Lovelace",
                    addendum = "von",
                )
            }

        val valueElement = result.container.querySelector(".profile-dialog__identity-value")!!
        val html = valueElement.innerHTML

        assertTrue(html.contains("<br"))
        assertTrue(valueElement.textContent!!.contains("Ada M."))
        assertTrue(valueElement.textContent!!.contains("von' Lovelace"))
    }

    @Test
    fun `enables previous navigation when movement allowed`() = runTest {
        renderIdentityProfileView {
            canNavigatePrevious = true
        }

        val previousButton = screen.getByRole("button", json("name" to text("button.identity.previous.aria")))

        assertFalse(previousButton.asDynamic().disabled as Boolean)
    }

    @Test
    fun `disables previous navigation when movement forbidden`() = runTest {
        renderIdentityProfileView {
            canNavigatePrevious = false
        }

        val previousButton = screen.getByRole("button", json("name" to text("button.identity.previous.aria")))

        assertTrue(previousButton.asDynamic().disabled as Boolean)
    }

    @Test
    fun `disables next navigation while busy`() = runTest {
        renderIdentityProfileView {
            isBusy = true
            canNavigateNext = true
        }

        val nextButton = screen.getByRole("button", json("name" to text("button.identity.next.aria")))

        assertTrue(nextButton.asDynamic().disabled as Boolean)
    }

    @Test
    fun `shows chevron icon when identities remain`() = runTest {
        renderIdentityProfileView {
            canNavigateNext = true
            isShowingLastIdentity = false
        }

        val nextButton = screen.getByRole("button", json("name" to text("button.identity.next.aria")))
        val chevronIcon = nextButton.querySelector("i.fa-chevron-right")
        val plusIcon = nextButton.querySelector("i.fa-user-plus")

        assertTrue(chevronIcon != null)
        assertNull(plusIcon)
    }

    @Test
    fun `renders progress indicators for total identities`() = runTest {
        val result =
            renderIdentityProfileView {
                identityTotal = 4
                identityIndex = 2
            }

        val progress = result.container.querySelector(".profile-dialog__identity-progress") as HTMLDivElement
        val segments = progress.querySelectorAll("span")
        val activeSegments =
            (0 until segments.length).map { index ->
                val element = segments.item(index) as HTMLElement
                element.className.contains("--active")
            }

        assertEquals(4, segments.length)
        assertEquals(listOf(false, false, true, false), activeSegments)
    }

    @Test
    fun `selects identity from progress click`() = runTest {
        var selectedIndex: Int? = null
        val result =
            renderIdentityProfileView {
                identityTotal = 5
                onSelect = { selectedIndex = it }
            }

        val progress = result.container.querySelector(".profile-dialog__identity-progress") as HTMLDivElement
        progress.asDynamic().getBoundingClientRect = {
            js("{ left: 10, width: 200 }")
        }
        fireEvent.click(progress, json("clientX" to 110))

        assertEquals(2, selectedIndex)
    }

    @Test
    fun `ignores progress click while busy`() = runTest {
        var selectedIndex: Int? = null
        val result =
            renderIdentityProfileView {
                isBusy = true
                identityTotal = 3
                onSelect = { selectedIndex = it }
            }

        val progress = result.container.querySelector(".profile-dialog__identity-progress") as HTMLDivElement
        progress.asDynamic().getBoundingClientRect = {
            js("{ left: 0, width: 300 }")
        }
        fireEvent.click(progress, json("clientX" to 150))

        assertNull(selectedIndex)
    }

    @Test
    fun `ignores progress click when identities unavailable`() = runTest {
        var selectedIndex: Int? = null
        val result =
            renderIdentityProfileView {
                identityTotal = 0
                onSelect = { selectedIndex = it }
            }

        val progress = result.container.querySelector(".profile-dialog__identity-progress") as HTMLDivElement
        progress.asDynamic().getBoundingClientRect = {
            js("{ left: 0, width: 100 }")
        }
        fireEvent.click(progress, json("clientX" to 50))

        assertNull(selectedIndex)
    }

    @Test
    fun `ignores progress click when element width missing`() = runTest {
        var selectedIndex: Int? = null
        val result =
            renderIdentityProfileView {
                identityTotal = 3
                onSelect = { selectedIndex = it }
            }

        val progress = result.container.querySelector(".profile-dialog__identity-progress") as HTMLDivElement
        progress.asDynamic().getBoundingClientRect = {
            js("{ left: 0, width: 0 }")
        }
        fireEvent.click(progress, json("clientX" to 10))

        assertNull(selectedIndex)
    }

    private fun text(key: String): String = AppModule.get<Translator>().translate(key)
}
