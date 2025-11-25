package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityToolsPresenterTest {
    @Test
    fun `presents actions with expected metadata`() {
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = true,
                canDownload = true,
                canCopy = true,
                canUpload = true,
                canPaste = true,
                onDelete = {},
                onDownload = {},
                onCopy = {},
                onUpload = {},
                onPaste = {},
            ),
        )

        assertEquals(5, actions.size)
        assertEquals("button.identity.delete.aria", actions[0].ariaKey)
        assertEquals("button.identity.delete.aria", actions[0].titleKey)
        assertEquals("fa fa-solid fa-trash-can", actions[0].iconClass)
        assertEquals("button.identity.copy.aria", actions[1].ariaKey)
        assertEquals("button.identity.copy.label", actions[1].titleKey)
        assertEquals("fa fa-solid fa-copy", actions[1].iconClass)
        assertEquals("button.identity.paste.aria", actions[2].ariaKey)
        assertEquals("button.identity.paste.label", actions[2].titleKey)
        assertEquals("fa fa-solid fa-paste", actions[2].iconClass)
        assertEquals("button.identity.download.aria", actions[3].ariaKey)
        assertEquals("button.identity.download.label", actions[3].titleKey)
        assertEquals("fa fa-solid fa-download", actions[3].iconClass)
        assertEquals("button.identity.upload.aria", actions[4].ariaKey)
        assertEquals("button.identity.upload.label", actions[4].titleKey)
        assertEquals("fa fa-solid fa-upload", actions[4].iconClass)
    }

    @Test
    fun `disables matching actions when capability is missing`() {
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = false,
                canDownload = true,
                canCopy = false,
                canUpload = false,
                canPaste = false,
                onDelete = {},
                onDownload = {},
                onCopy = {},
                onUpload = {},
                onPaste = {},
            ),
        )

        assertFalse(actions[0].isEnabled)
        assertFalse(actions[1].isEnabled)
        assertFalse(actions[2].isEnabled)
        assertTrue(actions[3].isEnabled)
        assertFalse(actions[4].isEnabled)
    }

    @Test
    fun `invokes handlers when trigger is executed`() {
        var deleteTriggered = false
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = true,
                canDownload = true,
                canCopy = true,
                canUpload = true,
                canPaste = true,
                onDelete = { deleteTriggered = true },
                onDownload = {},
                onCopy = {},
                onUpload = {},
                onPaste = {},
            ),
        )

        actions[0].onTrigger()

        assertTrue(deleteTriggered)
    }
}
