package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityAdminPresentationTest {
    @Test
    fun `maps admin badge matrix for connected and displayed identities`() {
        val scenarios =
            listOf(
                Scenario(connectedIsAdmin = false, displayedIsAdmin = false, headerBadge = false, profileBadge = false, avatarTitleKey = "connected.profile.avatar.title"),
                Scenario(connectedIsAdmin = false, displayedIsAdmin = true, headerBadge = false, profileBadge = true, avatarTitleKey = "connected.profile.avatar.title"),
                Scenario(connectedIsAdmin = true, displayedIsAdmin = false, headerBadge = true, profileBadge = false, avatarTitleKey = "connected.profile.avatar.admin.title"),
                Scenario(connectedIsAdmin = true, displayedIsAdmin = true, headerBadge = true, profileBadge = true, avatarTitleKey = "connected.profile.avatar.admin.title"),
            )

        scenarios.forEach { scenario ->
            val presentation =
                presentIdentityAdminStatus(
                    connectedIdentityIsAdmin = scenario.connectedIsAdmin,
                    displayedIdentityIsAdmin = scenario.displayedIsAdmin,
                )

            assertEquals(scenario.headerBadge, presentation.showsConnectedAdminBadge)
            assertEquals(scenario.profileBadge, presentation.showsDisplayedAdminBadge)
            assertEquals(scenario.avatarTitleKey, presentation.avatarTitleKey)
            assertEquals(scenario.headerBadge, presentation.canDeleteAsAdmin)
        }
    }

    private data class Scenario(
        val connectedIsAdmin: Boolean,
        val displayedIsAdmin: Boolean,
        val headerBadge: Boolean,
        val profileBadge: Boolean,
        val avatarTitleKey: String,
    )
}
