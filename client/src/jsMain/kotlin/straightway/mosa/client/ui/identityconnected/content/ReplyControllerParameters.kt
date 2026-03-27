package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import react.RefObject
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.content.QueryResult
import web.html.HTMLDivElement

internal data class ReplyControllerDependencies(
    val context: ConnectedViewAsyncContext,
    val replyPublisher: ReplyPublisher,
    val replySearchService: ReplySearchService,
    val repliesContainerRef: RefObject<HTMLDivElement>,
    val onPostPaneRequested: (QueryResult.Entry?) -> Unit,
)
