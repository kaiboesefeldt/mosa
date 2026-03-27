package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.Ref
import web.html.HTMLDivElement

internal data class PostsColumnProps(
    val visualsFactory: IdentityVisualsFactory,
    val content: ContentController,
    val moderation: ModerationController,
    val admin: AdminController,
    val selectedTopic: straightway.mosa.client.content.Topic?,
    val selectedPost: straightway.mosa.content.QueryResult.Entry?,
    val connectedGid: String?,
    val canDeleteAsAdmin: Boolean,
    val showBackToTopics: Boolean,
    val onBackToTopics: () -> Unit,
    val containerRef: Ref<HTMLDivElement>,
    val loadMoreRef: Ref<HTMLDivElement>,
)
