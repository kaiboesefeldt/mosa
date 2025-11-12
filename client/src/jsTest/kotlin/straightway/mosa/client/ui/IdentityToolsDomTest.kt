package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventTarget
import web.cssom.ClassName

class IdentityToolsDomTest {
    @Test
    fun `builds expanded class list when menu is visible`() {
        val className = identityToolsClass(true)

        assertEquals(
            ClassName("landing-dialog__identity-tools landing-dialog__identity-tools--expanded"),
            className,
        )
    }

    @Test
    fun `builds collapsed class list when menu is hidden`() {
        val className = identityToolsClass(false)

        assertEquals(ClassName("landing-dialog__identity-tools"), className)
    }

    @Test
    fun `detects container element as identity tools target`() {
        val container = createContainer()

        assertTrue(container.isIdentityToolsTarget())
    }

    @Test
    fun `detects descendants as identity tools targets`() {
        val container = createContainer()
        val child = document.createElement("button") as HTMLElement
        container.appendChild(child)

        assertTrue(child.isIdentityToolsTarget())
    }

    @Test
    fun `rejects targets outside identity tools container`() {
        val element = document.createElement("div") as HTMLElement

        assertFalse(element.isIdentityToolsTarget())
    }

    @Test
    fun `rejects null targets`() {
        val target: EventTarget? = null

        assertFalse(target.isIdentityToolsTarget())
    }

    private fun createContainer(): HTMLElement {
        val container = document.createElement("div") as HTMLElement
        container.className = "landing-dialog__identity-tools"
        return container
    }
}
