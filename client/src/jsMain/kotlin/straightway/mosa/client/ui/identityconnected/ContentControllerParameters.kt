package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.content.QueryResult
import straightway.mosa.client.ui.IdentitySessionSnapshot
import straightway.mosa.client.ui.IdentitySessionStore

internal data class ContentControllerDependencies(
    val context: ConnectedViewFeedbackContext,
    val repository: TopicRepository,
    val sessionStore: IdentitySessionStore,
    val searchService: TopicSearchService,
    val postCache: PostCache,
    val replyCache: ReplyCache,
    val postPublisher: PostPublisher,
    val postDeletionService: PostDeletionService,
    val allPostsDeletionService: AllPostsDeletionService,
    val replyPublisher: ReplyPublisher,
    val replySearchService: ReplySearchService,
    val isMobile: Boolean,
    val snapshotRefreshVersion: Int,
    val refs: ConnectedViewContentRefs,
)

internal data class ContentControllerCallbacks(
    val persistSessionSnapshot: suspend (((IdentitySessionSnapshot?) -> IdentitySessionSnapshot)) -> Unit,
    val onTopicPaneRequested: (Topic?) -> Unit,
    val onPostPaneRequested: (QueryResult.Entry?) -> Unit,
)
