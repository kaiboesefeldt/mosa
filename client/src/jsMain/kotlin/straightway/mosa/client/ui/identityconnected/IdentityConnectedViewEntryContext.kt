package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.content.Topic
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.ui.IdentityVisualsFactory
import straightway.mosa.content.QueryResult

internal data class IdentityConnectedViewEntryContext(
    val translator: Translator,
    val visualsFactory: IdentityVisualsFactory,
    val entry: QueryResult.Entry,
    val connectedGid: String?,
    val topic: Topic? = null,
)
