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
                canDeletePosts = true,
                canExportPosts = true,
                canFreezePosts = true,
                canRestorePosts = true,
                canDownload = true,
                canCopy = true,
                canUpload = true,
                canPaste = true,
                onDelete = {},
                onDeletePosts = {},
                onExportPosts = {},
                onFreezePosts = {},
                onRestorePosts = {},
                onDownload = {},
                onCopy = {},
                onUpload = {},
                onPaste = {},
            ),
        )

        assertEquals(9, actions.size)
        assertEquals("button.identity.delete.aria", actions[0].ariaKey)
        assertEquals("button.identity.delete.aria", actions[0].titleKey)
        assertEquals("fa fa-solid fa-trash-can", actions[0].iconClass)
        assertEquals("button.identity.delete-posts.aria", actions[1].ariaKey)
        assertEquals("button.identity.delete-posts.title", actions[1].titleKey)
        assertEquals("fa fa-solid fa-broom", actions[1].iconClass)
        assertEquals("button.identity.export-posts.aria", actions[2].ariaKey)
        assertEquals("button.identity.export-posts.label", actions[2].titleKey)
        assertEquals("fa fa-solid fa-file-csv", actions[2].iconClass)
        assertEquals("button.identity.freeze-posts.aria", actions[3].ariaKey)
        assertEquals("button.identity.freeze-posts.label", actions[3].titleKey)
        assertEquals("fa fa-solid fa-snowflake", actions[3].iconClass)
        assertEquals("button.identity.restore-posts.aria", actions[4].ariaKey)
        assertEquals("button.identity.restore-posts.label", actions[4].titleKey)
        assertEquals("fa fa-solid fa-rotate-left", actions[4].iconClass)
        assertEquals("button.identity.copy.aria", actions[5].ariaKey)
        assertEquals("button.identity.copy.label", actions[5].titleKey)
        assertEquals("fa fa-solid fa-copy", actions[5].iconClass)
        assertEquals("button.identity.paste.aria", actions[6].ariaKey)
        assertEquals("button.identity.paste.label", actions[6].titleKey)
        assertEquals("fa fa-solid fa-paste", actions[6].iconClass)
        assertEquals("button.identity.download.aria", actions[7].ariaKey)
        assertEquals("button.identity.download.label", actions[7].titleKey)
        assertEquals("fa fa-solid fa-download", actions[7].iconClass)
        assertEquals("button.identity.upload.aria", actions[8].ariaKey)
        assertEquals("button.identity.upload.label", actions[8].titleKey)
        assertEquals("fa fa-solid fa-upload", actions[8].iconClass)
    }

    @Test
    fun `groups actions by workflow`() {
        val groups = IdentityToolsPresenter.presentGroups(
            IdentityToolsPresenter.present(
                IdentityToolsCapabilities(
                    canDelete = true,
                    canDeletePosts = true,
                    canExportPosts = true,
                    canFreezePosts = true,
                    canRestorePosts = true,
                    canDownload = true,
                    canCopy = true,
                    canUpload = true,
                    canPaste = true,
                    onDelete = {},
                    onDeletePosts = {},
                    onExportPosts = {},
                    onFreezePosts = {},
                    onRestorePosts = {},
                    onDownload = {},
                    onCopy = {},
                    onUpload = {},
                    onPaste = {},
                ),
            ),
        )

        assertEquals(4, groups.size)
        assertEquals("profile.identity.tools.group.keys", groups[0].titleKey)
        assertEquals(
            listOf(
                "button.identity.copy.aria",
                "button.identity.paste.aria",
                "button.identity.download.aria",
                "button.identity.upload.aria",
            ),
            groups[0].actions.map { it.ariaKey },
        )
        assertEquals("profile.identity.tools.group.content", groups[1].titleKey)
        assertEquals(
            listOf(
                "button.identity.export-posts.aria",
                "button.identity.delete-posts.aria",
            ),
            groups[1].actions.map { it.ariaKey },
        )
        assertEquals("profile.identity.tools.group.freeze", groups[2].titleKey)
        assertEquals(
            listOf(
                "button.identity.freeze-posts.aria",
                "button.identity.restore-posts.aria",
            ),
            groups[2].actions.map { it.ariaKey },
        )
        assertEquals("profile.identity.tools.group.identity", groups[3].titleKey)
        assertEquals(listOf("button.identity.delete.aria"), groups[3].actions.map { it.ariaKey })
    }

    @Test
    fun `disables matching actions when capability is missing`() {
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = false,
                canDeletePosts = true,
                canExportPosts = false,
                canFreezePosts = false,
                canRestorePosts = true,
                canDownload = true,
                canCopy = false,
                canUpload = false,
                canPaste = false,
                onDelete = {},
                onDeletePosts = {},
                onExportPosts = {},
                onFreezePosts = {},
                onRestorePosts = {},
                onDownload = {},
                onCopy = {},
                onUpload = {},
                onPaste = {},
            ),
        )

        assertFalse(actions[0].isEnabled)
        assertTrue(actions[1].isEnabled)
        assertFalse(actions[2].isEnabled)
        assertFalse(actions[3].isEnabled)
        assertTrue(actions[4].isEnabled)
        assertFalse(actions[5].isEnabled)
        assertFalse(actions[6].isEnabled)
        assertTrue(actions[7].isEnabled)
        assertFalse(actions[8].isEnabled)
    }

    @Test
    fun `invokes handlers when trigger is executed`() {
        var deleteTriggered = false
        val actions = IdentityToolsPresenter.present(
            IdentityToolsCapabilities(
                canDelete = true,
                canDeletePosts = true,
                canExportPosts = true,
                canFreezePosts = true,
                canRestorePosts = true,
                canDownload = true,
                canCopy = true,
                canUpload = true,
                canPaste = true,
                onDelete = { deleteTriggered = true },
                onDeletePosts = {},
                onExportPosts = {},
                onFreezePosts = {},
                onRestorePosts = {},
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
