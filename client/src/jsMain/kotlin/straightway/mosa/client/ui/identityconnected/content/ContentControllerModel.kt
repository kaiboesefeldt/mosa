package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

internal data class ContentController(
    val state: ContentControllerState,
    val actions: ContentControllerActions,
    val lookups: ContentControllerLookups,
)
