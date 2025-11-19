package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.Date
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.dom.events.ChangeEvent
import react.dom.events.MouseEvent
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.topic.Topic
import straightway.mosa.client.topic.TopicRepository
import straightway.mosa.client.topic.TopicPosts
import straightway.mosa.client.topic.TopicSearchService
import straightway.mosa.client.topic.PostPublisher
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalNameGenerator
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
    val repository = ConnectedViewInjector.get<TopicRepository>()
    val sessionStore = ConnectedViewInjector.get<IdentitySessionStore>()
    val searchService = ConnectedViewInjector.get<TopicSearchService>()
    val nameGenerator = ConnectedViewInjector.get<FictionalNameGenerator>()
    val avatarGenerator = ConnectedViewInjector.get<DiceBearAvatarGenerator>()
    val postPublisher = ConnectedViewInjector.get<PostPublisher>()
    val visualsFactory =
        useMemo(nameGenerator, avatarGenerator) {
            IdentityVisualsFactory(nameGenerator, avatarGenerator)
        }
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
    val (isComposerOpen, setComposerOpen) = useState(false)
    val (composerText, setComposerText) = useState("")
    val (isPublishing, setPublishing) = useState(false)
    val (publishError, setPublishError) = useState<String?>(null)

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

    fun removeTopic(topic: Topic) {
        val removalIndex = topics.indexOfFirst { it.title == topic.title }
        val wasSelected = selectedTopic?.title == topic.title
        if (removalIndex < 0) return
        scope.launch {
            val updated = repository.remove(topic)
            setTopics(updated)
            if (wasSelected) {
                selectTopic(updated.getOrNull(removalIndex))
            }
        }
    }

    fun showProfile() {
        persistProfileVisibility(true)
    }

    fun hideProfile() {
        persistProfileVisibility(false)
    }

    fun openComposer() {
        setPublishError(null)
        setComposerOpen(true)
    }

    fun closeComposer() {
        if (isPublishing) return
        setPublishError(null)
        setComposerOpen(false)
    }

    fun sendPost() {
        if (isPublishing) return
        val text = composerText.trim()
        if (text.isEmpty()) return
        val topicForRefresh = selectedTopic
        setPublishing(true)
        setPublishError(null)
        scope.launch {
            try {
                val success = postPublisher.publish(text)
                if (success) {
                    setComposerText("")
                    setComposerOpen(false)
                    refreshPosts(topicForRefresh)
                } else {
                    setPublishError(translator.translate("connected.composer.error"))
                }
            } finally {
                setPublishing(false)
            }
        }
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
            topicColumn(translator, topics, selectedTopic, ::addTopic, ::selectTopic, ::removeTopic)
            postsColumn(
                translator = translator,
                visualsFactory = visualsFactory,
                posts = posts,
                isLoading = isLoadingPosts,
            )
            placeholderColumn(
                title = translator.translate("connected.replies.title"),
                message = translator.translate("connected.replies.empty"),
                modifier = "topic-board__column--replies",
            )
        }
        floatingComposerButton(
            label = translator.translate("connected.composer.open"),
            onActivate = ::openComposer,
        )
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

    if (isComposerOpen) {
        composerDialog(
            translator = translator,
            text = composerText,
            onTextChange = { next -> setComposerText(next) },
            onClose = ::closeComposer,
            onSend = ::sendPost,
            isPublishing = isPublishing,
            errorText = publishError,
        )
    }
}

private fun ChildrenBuilder.topicColumn(
    translator: Translator,
    topics: List<Topic>,
    selectedTopic: Topic?,
    onAddTopic: () -> Unit,
    onSelectTopic: (Topic) -> Unit,
    onRemoveTopic: (Topic) -> Unit,
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
                button {
                    className = ClassName("topic-card__delete")
                    ariaLabel = translator.translate("connected.topics.delete")
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        onRemoveTopic(topic)
                    }
                    +"×"
                }
                span {
                    className = ClassName("topic-card__title")
                    +topic.title
                }
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
    visualsFactory: IdentityVisualsFactory,
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
        else ->
            posts.forEach { topicPosts ->
                topicPosts.entries.forEach { entry ->
                    postCard(visualsFactory, entry)
                }
            }
    }
}

private fun ChildrenBuilder.postCard(
    visualsFactory: IdentityVisualsFactory,
    entry: QueryResult.Entry,
) {
    val authorVisuals = visualsFactory.createFromGid(entry.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(entry.storedAt)
    val similarity = formatSimilarity(entry.similarity)
    div {
        key = entry.id
        className = ClassName("post-card")
        div {
            className = ClassName("post-card__author-row")
            img {
                className = ClassName("post-card__avatar")
                src = authorVisuals.avatar
                alt = authorName
            }
            div {
                className = ClassName("post-card__author-details")
                span {
                    className = ClassName("post-card__author-name")
                    +authorName
                }
                div {
                    className = ClassName("post-card__meta")
                    span {
                        className = ClassName("post-card__timestamp")
                        +timestamp
                    }
                    span {
                        className = ClassName("post-card__similarity")
                        +similarity
                    }
                }
            }
        }
        div {
            className = ClassName("post-card__content")
            +entry.content
        }
    }
}

private fun ChildrenBuilder.floatingComposerButton(
    label: String,
    onActivate: () -> Unit,
) = button {
    className = ClassName("composer-button")
    ariaLabel = label
    title = label
    onClick = { onActivate() }
    +"+"
}

private fun ChildrenBuilder.composerDialog(
    translator: Translator,
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    isPublishing: Boolean,
    errorText: String?,
) = div {
    className = ClassName("composer-overlay")
    div {
        className = ClassName("composer-dialog")
        div {
            className = ClassName("composer-dialog__header")
            span {
                className = ClassName("composer-dialog__title")
                +translator.translate("connected.composer.title")
            }
            button {
                className = ClassName("composer-dialog__close")
                ariaLabel = translator.translate("connected.composer.close")
                onClick = { onClose() }
                +"x"
            }
        }
        div {
            className = ClassName("composer-dialog__body")
            textarea {
                className = ClassName("composer-dialog__textarea")
                placeholder = translator.translate("connected.composer.placeholder")
                value = text
                disabled = isPublishing
                rows = 5
                onChange = { event: ChangeEvent<HTMLTextAreaElement> ->
                    onTextChange(event.target.value)
                }
            }
            if (errorText != null) {
                div {
                    className = ClassName("composer-dialog__error")
                    +errorText
                }
            }
        }
        div {
            className = ClassName("composer-dialog__footer")
            button {
                className = ClassName("button button--primary composer-dialog__send")
                disabled = text.trim().isEmpty() || isPublishing
                onClick = { onSend() }
                +translator.translate("connected.composer.send")
            }
        }
    }
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

private fun formatTimestamp(epochSeconds: Long): String =
    Date(epochSeconds.toDouble() * 1000).toLocaleString()

private fun formatSimilarity(similarity: Float): String =
    (similarity.toDouble().asDynamic().toFixed(3) as String)
