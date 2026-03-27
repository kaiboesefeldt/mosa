package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.content.Topic
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.ui.IdentityVisualsFactory
import straightway.mosa.content.QueryResult

internal data class EntryContext(
    val translator: Translator,
    val visualsFactory: IdentityVisualsFactory,
    val entry: QueryResult.Entry,
    val connectedGid: String?,
    val topic: Topic? = null,
)
