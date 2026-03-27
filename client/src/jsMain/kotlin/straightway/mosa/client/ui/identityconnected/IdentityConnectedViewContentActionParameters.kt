package straightway.mosa.client.ui.identityconnected

import react.RefObject
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.content.QueryResult
import web.html.HTMLDivElement

internal data class IdentityConnectedViewContentActionDependencies(
    val context: IdentityConnectedViewFeedbackContext,
    val repository: TopicRepository,
    val searchService: TopicSearchService,
    val postCache: PostCache,
    val postPublisher: PostPublisher,
    val postDeletionService: PostDeletionService,
    val allPostsDeletionService: AllPostsDeletionService,
    val postsContainerRef: RefObject<HTMLDivElement>,
)

internal data class IdentityConnectedViewContentTopicsModel(
    val topics: List<Topic>,
    val selectedTopic: Topic?,
    val topicFilters: Map<String, Float>,
    val updateTopics: (List<Topic>) -> Unit,
    val selectTopic: (Topic?) -> Unit,
    val persistFilters: (Map<String, Float>) -> Unit,
)

internal data class IdentityConnectedViewContentComposerModel(
    val text: String,
    val isPublishing: Boolean,
    val setOpen: (Boolean) -> Unit,
    val setText: (String) -> Unit,
    val setPublishing: (Boolean) -> Unit,
    val setError: (String?) -> Unit,
)

internal data class IdentityConnectedViewContentDeletionModel(
    val isDeletingPost: Boolean,
    val isDeletingAllPosts: Boolean,
    val setCandidate: (QueryResult.Entry?) -> Unit,
    val setError: (String?) -> Unit,
    val setDeletingPost: (Boolean) -> Unit,
    val setDeletingAllPosts: (Boolean) -> Unit,
    val removePost: (String) -> Unit,
    val removePosts: (Collection<String>) -> Unit,
)

internal data class IdentityConnectedViewContentPostFeedModel(
    val posts: List<TopicPosts>,
    val isLoadingPosts: Boolean,
    val isAppendingPosts: Boolean,
    val refreshPosts: suspend (Topic?) -> Boolean,
    val updatePosts: (List<TopicPosts>) -> Unit,
    val setAppending: (Boolean) -> Unit,
)
