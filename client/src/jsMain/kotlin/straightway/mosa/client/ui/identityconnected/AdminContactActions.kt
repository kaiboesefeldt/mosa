package straightway.mosa.client.ui.identityconnected

internal data class AdminContactActions(
    val canComposeAdminMessage: Boolean,
    val onToggleAdminUserCard: (String, String, String, String) -> Unit,
)
