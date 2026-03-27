package straightway.mosa.client.ui.identityconnected.moderation

import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*

internal data class ModerationController(
    val state: ModerationControllerState,
    val refs: ModerationControllerRefs,
    val actions: ModerationControllerActions,
)
