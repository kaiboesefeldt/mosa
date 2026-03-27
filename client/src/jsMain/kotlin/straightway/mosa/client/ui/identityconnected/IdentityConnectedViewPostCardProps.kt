package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.content.Topic
import straightway.mosa.content.QueryResult

internal data class IdentityConnectedViewPostCardProps(
    val entryContext: IdentityConnectedViewEntryContext,
    val entryActions: IdentityConnectedViewEntryActions,
    val topic: Topic,
    val isFilterEntry: Boolean,
    val onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    val isSelected: Boolean,
    val onSelect: () -> Unit,
)
