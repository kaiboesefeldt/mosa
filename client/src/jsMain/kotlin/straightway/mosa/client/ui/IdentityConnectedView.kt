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
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.topic.Topic
import straightway.mosa.client.topic.TopicRepository
import web.cssom.ClassName

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
    val repository = ConnectedViewInjector.get<TopicRepository>()
    val sessionStore = ConnectedViewInjector.get<IdentitySessionStore>()
    val scope = useMemo(*noDependencies) { MainScope() }
    val (topics, setTopics) = useState<List<Topic>>(emptyList())
    val initialProfileVisibility =
        useMemo(sessionStore) {
            sessionStore.load()?.isProfileVisible ?: false
        }
    val (isProfileVisible, setProfileVisible) = useState(initialProfileVisibility)

    useEffect(arrayOf(repository)) {
        scope.launch {
            setTopics(repository.all())
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
            setTopics(repository.add(sanitized))
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
            topicColumn(translator, topics, ::addTopic)
            placeholderColumn(
                title = translator.translate("connected.posts.title"),
                message = translator.translate("connected.posts.empty"),
                modifier = "topic-board__column--posts",
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
    onAddTopic: () -> Unit,
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
            div {
                key = topic.title
                className = ClassName("topic-card")
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

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()
