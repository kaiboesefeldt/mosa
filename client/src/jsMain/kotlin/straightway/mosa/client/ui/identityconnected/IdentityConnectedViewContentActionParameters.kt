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

internal data class IdentityConnectedViewContentActionState(
    val topics: () -> List<Topic>,
    val selectedTopic: () -> Topic?,
    val topicFilters: () -> Map<String, Float>,
    val posts: () -> List<TopicPosts>,
    val isLoadingPosts: () -> Boolean,
    val isAppendingPosts: () -> Boolean,
    val isPublishing: () -> Boolean,
    val isDeletingPost: () -> Boolean,
    val isDeletingAllPosts: () -> Boolean,
    val composerText: () -> String,
)

internal data class IdentityConnectedViewContentActionMutations(
    val setTopics: (List<Topic>) -> Unit,
    val setPosts: (List<TopicPosts>) -> Unit,
    val selectTopic: (Topic?) -> Unit,
    val persistFilters: (Map<String, Float>) -> Unit,
    val refreshPosts: suspend (Topic?) -> Boolean,
    val setAppendingState: (Boolean) -> Unit,
    val setComposerOpen: (Boolean) -> Unit,
    val setComposerText: (String) -> Unit,
    val setPublishing: (Boolean) -> Unit,
    val setPublishError: (String?) -> Unit,
    val setDeleteCandidate: (QueryResult.Entry?) -> Unit,
    val setDeleteError: (String?) -> Unit,
    val setDeletingPost: (Boolean) -> Unit,
    val setDeletingAllPosts: (Boolean) -> Unit,
    val removePost: (String) -> Unit,
    val removePosts: (Collection<String>) -> Unit,
)
