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
                canUpload = true,
                onDelete = {},
                onDownload = {},
                onUpload = {},
            ),
        )

        assertEquals(3, actions.size)
        assertEquals("button.identity.delete.aria", actions[0].ariaKey)
        assertEquals("button.identity.delete.aria", actions[0].titleKey)
        assertEquals("fa fa-solid fa-trash-can", actions[0].iconClass)
        assertEquals("button.identity.download.aria", actions[1].ariaKey)
        assertEquals("button.identity.download.label", actions[1].titleKey)
        assertEquals("fa fa-solid fa-download", actions[1].iconClass)
        assertEquals("button.identity.upload.aria", actions[2].ariaKey)
        assertEquals("button.identity.upload.label", actions[2].titleKey)
        assertEquals("fa fa-solid fa-upload", actions[2].iconClass)
    }

    @Test
    fun `disables matching actions when capability is missing`() {
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = false,
                canDownload = true,
                canUpload = false,
                onDelete = {},
                onDownload = {},
                onUpload = {},
            ),
        )

        assertFalse(actions[0].isEnabled)
        assertTrue(actions[1].isEnabled)
        assertFalse(actions[2].isEnabled)
    }

    @Test
    fun `invokes handlers when trigger is executed`() {
        var deleteTriggered = false
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = true,
                canDownload = true,
                canUpload = true,
                onDelete = { deleteTriggered = true },
                onDownload = {},
                onUpload = {},
            ),
        )

        actions[0].onTrigger()

        assertTrue(deleteTriggered)
    }
}
