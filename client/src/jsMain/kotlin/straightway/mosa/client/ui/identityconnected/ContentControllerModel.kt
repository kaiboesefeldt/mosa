package straightway.mosa.client.ui.identityconnected

internal data class ContentController(
    val state: ContentControllerState,
    val actions: ContentControllerActions,
    val lookups: ContentControllerLookups,
)
