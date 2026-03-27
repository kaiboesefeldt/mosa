package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

internal data class AdminContactActions(
    val canComposeAdminMessage: Boolean,
    val onToggleAdminUserCard: (String, String, String, String) -> Unit,
)
