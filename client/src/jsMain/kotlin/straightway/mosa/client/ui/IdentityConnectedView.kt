package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.topic.Topic
import straightway.mosa.client.topic.TopicRepository
import straightway.mosa.client.topic.TopicPosts
import straightway.mosa.client.topic.TopicSearchService
import straightway.mosa.content.QueryResult
import web.cssom.ClassName

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
    val repository = ConnectedViewInjector.get<TopicRepository>()
    val sessionStore = ConnectedViewInjector.get<IdentitySessionStore>()
    val searchService = ConnectedViewInjector.get<TopicSearchService>()
    val scope = useMemo(*noDependencies) { MainScope() }
    val (topics, setTopics) = useState<List<Topic>>(emptyList())
    val (selectedTopic, setSelectedTopic) = useState<Topic?>(null)
    val (posts, setPosts) = useState<List<TopicPosts>>(emptyList())
    val (isLoadingPosts, setLoadingPosts) = useState(false)
    val postsRequestVersion = useRef(0)
    val initialProfileVisibility =
        useMemo(sessionStore) {
            sessionStore.load()?.isProfileVisible ?: false
    }
    val (isProfileVisible, setProfileVisible) = useState(initialProfileVisibility)

    suspend fun refreshPosts(currentTopic: Topic?) {
        val nextVersion = (postsRequestVersion.current ?: 0) + 1
        postsRequestVersion.current = nextVersion
        val version = nextVersion
        if (currentTopic == null) {
            setPosts(emptyList())
            setLoadingPosts(false)
            return
        }
        setLoadingPosts(true)
        val results =
            runCatching { searchService.fetchFor(listOf(currentTopic)) }
                .getOrElse {
                    if (postsRequestVersion.current == version) {
                        setLoadingPosts(false)
                    }
                    return
                }
        if (postsRequestVersion.current != version) return
        setPosts(results)
        setLoadingPosts(false)
    }

    fun selectTopic(topic: Topic?) {
        if (topic == selectedTopic) return
        setSelectedTopic(topic)
        scope.launch {
            refreshPosts(topic)
        }
    }

    useEffect(*noDependencies) {
        scope.launch {
            val loadedTopics = repository.all()
            setTopics(loadedTopics)
            selectTopic(loadedTopics.firstOrNull())
        }
    }

    useEffect(*noDependencies) {
        try {
            awaitCancellation()
        } finally {
            scope.cancel()
        }
    }

    val avatarDescription = translator.translate("connected.profile.avatar.title")

    fun persistProfileVisibility(visible: Boolean) {
        setProfileVisible(visible)
        val currentSnapshot = sessionStore.load()
        sessionStore.save(
            IdentitySessionSnapshot(
                connectedIdentityGid = currentSnapshot?.connectedIdentityGid,
                isProfileVisible = visible,
            ),
        )
    }

    fun addTopic() {
        val text = window.prompt(translator.translate("connected.topics.prompt")) ?: return
        val sanitized = text.trim()
        if (sanitized.isEmpty()) return
        scope.launch {
            val updated = repository.add(sanitized)
            setTopics(updated)
            selectTopic(updated.lastOrNull())
        }
    }

    fun showProfile() {
        persistProfileVisibility(true)
    }

    fun hideProfile() {
        persistProfileVisibility(false)
    }

    div {
        className = ClassName("connected-shell")
        div {
            className = ClassName("connected-shell__header")
            div {
                className = ClassName("connected-shell__profile")
                span {
                    className = ClassName("connected-shell__identity-name")
                    +props.identityName
                }
                button {
                    className = ClassName("connected-shell__avatar-button")
                    ariaLabel = translator.translate("connected.profile.avatar.aria")
                    title = avatarDescription
                    onClick = { showProfile() }
                    val avatar = props.profile.identityAvatar
                    if (avatar != null) {
                        img {
                            className = ClassName("connected-shell__avatar-image")
                            src = avatar
                            alt = avatarDescription
                        }
                    } else {
                        span {
                            className = ClassName("connected-shell__avatar-fallback")
                            +(props.identityName.firstOrNull()?.uppercaseChar()?.toString() ?: "?")
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("topic-board")
            topicColumn(translator, topics, selectedTopic, ::addTopic, ::selectTopic)
            postsColumn(
                translator = translator,
                posts = posts,
                isLoading = isLoadingPosts,
            )
            placeholderColumn(
                title = translator.translate("connected.replies.title"),
                message = translator.translate("connected.replies.empty"),
                modifier = "topic-board__column--replies",
            )
        }
    }

    if (isProfileVisible) {
        IdentityProfileView {
            applyFrom(props.profile)
            val connectProfile = props.profile.onConnect
            onClose = { hideProfile() }
            onConnect = {
                connectProfile()
                hideProfile()
            }
        }
    }
}

private fun ChildrenBuilder.topicColumn(
    translator: Translator,
    topics: List<Topic>,
    selectedTopic: Topic?,
    onAddTopic: () -> Unit,
    onSelectTopic: (Topic) -> Unit,
) = div {
    className = ClassName("topic-board__column topic-board__column--topics")
    div {
        className = ClassName("topic-board__column-header")
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.topics.title")
        }
        button {
            className = ClassName("button button--primary topic-board__add-button")
            onClick = { onAddTopic() }
            +translator.translate("connected.topics.add")
        }
    }
    if (topics.isEmpty()) {
        div {
            className = ClassName("topic-board__empty")
            +translator.translate("connected.topics.empty")
        }
    } else {
        topics.forEach { topic ->
            val cardClass =
                if (topic == selectedTopic) {
                    "topic-card topic-card--selected"
                } else {
                    "topic-card"
                }
            div {
                key = topic.title
                className = ClassName(cardClass)
                onClick = { onSelectTopic(topic) }
                +topic.title
            }
        }
    }
}

private fun ChildrenBuilder.placeholderColumn(
    title: String,
    message: String,
    modifier: String,
) = div {
    className = ClassName("topic-board__column $modifier")
    span {
        className = ClassName("topic-board__column-title")
        +title
    }
    div {
        className = ClassName("topic-board__empty")
        +message
    }
}

private fun ChildrenBuilder.postsColumn(
    translator: Translator,
    posts: List<TopicPosts>,
    isLoading: Boolean,
) = div {
    className = ClassName("topic-board__column topic-board__column--posts")
    span {
        className = ClassName("topic-board__column-title")
        +translator.translate("connected.posts.title")
    }
    when {
        isLoading ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.loading")
            }
        posts.isEmpty() ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.empty")
            }
        else -> posts.forEach { topicPosts ->
            topicPosts.entries.forEach { entry ->
                postCard(topicPosts.topic, entry)
            }
        }
    }
}

private fun ChildrenBuilder.postCard(topic: Topic, entry: QueryResult.Entry) = div {
    key = entry.id
    className = ClassName("post-card")
    span {
        className = ClassName("post-card__topic")
        +topic.title
    }
    div {
        className = ClassName("post-card__content")
        +entry.content
    }
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()
