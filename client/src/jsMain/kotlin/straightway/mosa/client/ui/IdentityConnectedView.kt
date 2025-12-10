@file:OptIn(ExperimentalWasmJsInterop::class)

package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.Date
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsName
import kotlin.js.unsafeCast
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.dom.events.ChangeEvent
import react.dom.events.MouseEvent
import react.useEffect
import react.useMemo
import react.Ref
import react.useRef
import react.useState
import web.dom.Element
import web.html.HTMLDivElement
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.TopicFilters
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalNameGenerator
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

private external interface JsIntersectionObserverEntry {
    val isIntersecting: Boolean
}

private external interface JsIntersectionObserverOptions {
    var root: Element?
    var rootMargin: String?
    var threshold: dynamic
}

@JsName("IntersectionObserver")
private external class JsIntersectionObserver(
    callback: (Array<JsIntersectionObserverEntry>, JsIntersectionObserver) -> Unit,
    options: JsIntersectionObserverOptions = definedExternally,
) {
    fun observe(target: Element)
    fun disconnect()
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
    val repository = ConnectedViewInjector.get<TopicRepository>()
    val sessionStore = ConnectedViewInjector.get<IdentitySessionStore>()
    val searchService = ConnectedViewInjector.get<TopicSearchService>()
    val replySearchService = ConnectedViewInjector.get<ReplySearchService>()
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
    val (selectedPost, setSelectedPost) = useState<QueryResult.Entry?>(null)
    val (replies, setReplies) = useState<ThreadReplies?>(null)
    val (isLoadingReplies, setLoadingReplies) = useState(false)
    val (topicFilters, setTopicFilters) = useState<Map<String, Float>>(emptyMap())
    val (isLoadingPosts, setLoadingPosts) = useState(false)
    val postsContainerRef = useRef<HTMLDivElement>()
    val loadMoreRef = useRef<HTMLDivElement>()
    val isAppendingPostsRef = useRef(false)
    val postsRequestVersion = useRef(0)
    val repliesContainerRef = useRef<HTMLDivElement>()
    val repliesLoadMoreRef = useRef<HTMLDivElement>()
    val isAppendingRepliesRef = useRef(false)
    val repliesRequestVersion = useRef(0)
    val (isProfileVisible, setProfileVisible) = useState(false)
    val (isComposerOpen, setComposerOpen) = useState(false)
    val (composerText, setComposerText) = useState("")
    val (isPublishing, setPublishing) = useState(false)
    val (publishError, setPublishError) = useState<String?>(null)

    useEffect(sessionStore) {
        scope.launch {
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            setProfileVisible(snapshot?.isProfileVisible ?: false)
        }
    }

    suspend fun persistSessionSnapshot(
        builder: (IdentitySessionSnapshot?) -> IdentitySessionSnapshot,
    ) {
        val currentSnapshot = runCatching { sessionStore.load() }.getOrNull()
        sessionStore.save(builder(currentSnapshot))
    }

    fun updateTopicFilterState(filters: Map<String, Float>) {
        if (topics.isEmpty()) return
        val nextTopics =
            topics.map { topic ->
                val nextFilter = filters[topic.title]
                if (topic.scoreMustBeAbove == nextFilter) {
                    topic
                } else {
                    topic.copy(scoreMustBeAbove = nextFilter)
                }
            }
        if (nextTopics == topics) return
        val selectedTitle = selectedTopic?.title
        setTopics(nextTopics)
        if (selectedTitle != null) {
            val refreshed = nextTopics.firstOrNull { it.title == selectedTitle } ?: return
            setSelectedTopic(refreshed)
        }
    }

    fun filtersFromTopics(items: List<Topic>): Map<String, Float> =
        items.mapNotNull { topic ->
            val value = topic.scoreMustBeAbove
            if (value == null) {
                null
            } else {
                topic.title to value
            }
        }.toMap()

    fun rootIdFor(entry: QueryResult.Entry): String = entry.rootId ?: entry.id

    fun setAppendingState(active: Boolean) {
        isAppendingPostsRef.current = active
    }

    fun clearReplies() {
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        setSelectedPost(null)
        setReplies(null)
        setLoadingReplies(false)
        isAppendingRepliesRef.current = false
    }

    fun updateTopicPosts(updated: TopicPosts) {
        setPosts { current ->
            val replaced =
                current
                    .map { topicPosts ->
                        if (topicPosts.topic.title == updated.topic.title) {
                            updated
                        } else {
                            topicPosts
                        }
                    }
            if (replaced.any { it.topic.title == updated.topic.title }) {
                replaced
            } else {
                replaced + updated
            }
        }
    }

    suspend fun refreshPosts(currentTopic: Topic?) {
        val nextVersion = (postsRequestVersion.current ?: 0) + 1
        postsRequestVersion.current = nextVersion
        val version = nextVersion
        postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        if (currentTopic == null) {
            setPosts(emptyList())
            setLoadingPosts(false)
            return
        }
        setAppendingState(false)
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

    fun persistFilters(updated: Map<String, Float>) {
        val sanitized = TopicFilters.sanitize(updated)
        if (sanitized == topicFilters) return
        updateTopicFilterState(sanitized)
        setTopicFilters(sanitized)
        scope.launch {
            repository.applyFilters(sanitized)
        }
    }

    fun selectTopic(topic: Topic?) {
        if (topic == selectedTopic) return
        clearReplies()
        setSelectedTopic(topic)
        scope.launch {
            persistSessionSnapshot { snapshot ->
                IdentitySessionSnapshot(
                    connectedIdentityGid = snapshot?.connectedIdentityGid,
                    isProfileVisible = snapshot?.isProfileVisible ?: false,
                    selectedTopicTitle = topic?.title,
                )
            }
            refreshPosts(topic)
        }
    }

    useEffect(*noDependencies) {
        scope.launch {
            val loadedTopics = repository.all()
            setTopics(loadedTopics)
            val filters = filtersFromTopics(loadedTopics)
            setTopicFilters(filters)
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            val storedSelection = snapshot?.selectedTopicTitle
            val initialSelection =
                loadedTopics.firstOrNull { it.title == storedSelection } ?: loadedTopics.firstOrNull()
            selectTopic(initialSelection)
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
        scope.launch {
            persistSessionSnapshot { snapshot ->
                IdentitySessionSnapshot(
                    connectedIdentityGid = snapshot?.connectedIdentityGid,
                    isProfileVisible = visible,
                    selectedTopicTitle = snapshot?.selectedTopicTitle,
                )
            }
        }
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
            if (topicFilters.containsKey(topic.title)) {
                persistFilters(topicFilters - topic.title)
            }
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

    fun clearTopicFilter(topic: Topic) {
        val previousFilter = topicFilters[topic.title] ?: return
        persistFilters(topicFilters - topic.title)
        val clearedTopic = topic.copy(scoreMustBeAbove = null)
        scope.launch {
            searchService.filterCleared(clearedTopic, previousFilter)
            if (selectedTopic?.title == topic.title) {
                refreshPosts(clearedTopic)
            }
        }
    }

    fun toggleTopicFilter(topic: Topic, entry: QueryResult.Entry) {
        val currentScore = topicFilters[topic.title]
        if (currentScore != null && currentScore == entry.similarity) {
            clearTopicFilter(topic)
            return
        }
        persistFilters(topicFilters + (topic.title to entry.similarity))
    }

    fun loadMorePosts() {
        val topic = selectedTopic ?: return
        if (isLoadingPosts || isAppendingPostsRef.current == true) return
        val currentPosts = posts.firstOrNull { it.topic.title == topic.title } ?: return
        if (currentPosts.entries.isEmpty()) return
        val hasAnchorEntry =
            currentPosts.entries.any { entry ->
                val threshold = topic.scoreMustBeAbove
                threshold == null || entry.similarity > threshold
            }
        if (!hasAnchorEntry) return
        postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        setAppendingState(true)
        scope.launch {
            try {
                val additionalPosts = searchService.fetchMore(topic)
                if (additionalPosts != null) {
                    updateTopicPosts(additionalPosts)
                }
            } finally {
                setAppendingState(false)
            }
        }
    }

    fun loadMoreReplies() {
        val rootId = replies?.rootId ?: return
        if (isLoadingReplies || isAppendingRepliesRef.current == true) return
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        isAppendingRepliesRef.current = true
        scope.launch {
            try {
                val additionalReplies = replySearchService.fetchMore(rootId)
                if (additionalReplies != null) {
                    setReplies(additionalReplies)
                }
            } finally {
                isAppendingRepliesRef.current = false
            }
        }
    }

    fun selectPost(entry: QueryResult.Entry) {
        val rootId = rootIdFor(entry)
        val nextVersion = (repliesRequestVersion.current ?: 0) + 1
        repliesRequestVersion.current = nextVersion
        setSelectedPost(entry)
        setReplies(null)
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        isAppendingRepliesRef.current = false
        setLoadingReplies(true)
        scope.launch {
            val result =
                runCatching { replySearchService.fetchFor(rootId) }
                    .getOrElse {
                        if (repliesRequestVersion.current == nextVersion) {
                            setReplies(null)
                            setLoadingReplies(false)
                        }
                        return@launch
                    }
            if (repliesRequestVersion.current != nextVersion) return@launch
            setReplies(result)
            setLoadingReplies(false)
        }
    }

    useEffect(arrayOf(posts, selectedTopic)) {
        val target = loadMoreRef.current ?: return@useEffect
        val container = postsContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        options.root = container
        options.rootMargin = "48px"
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) {
                        loadMorePosts()
                    }
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    useEffect(arrayOf(replies, selectedPost)) {
        val target = repliesLoadMoreRef.current ?: return@useEffect
        val container = repliesContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        options.root = container
        options.rootMargin = "48px"
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) {
                        loadMoreReplies()
                    }
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    div {
        className = ClassName("connected-shell")
        div {
            className = ClassName("connected-shell__header")
            div {
                className = ClassName("connected-shell__brand")
                img {
                    className = ClassName("connected-shell__brand-logo")
                    src = "/logo.png"
                    alt = translator.translate("connected.brand.alt")
                }
                span {
                    className = ClassName("connected-shell__brand-name")
                    +translator.translate("connected.brand.name")
                }
            }
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
                selectedTopic = selectedTopic,
                selectedPost = selectedPost,
                isLoading = isLoadingPosts,
                activeFilters = topicFilters,
                onToggleFilter = ::toggleTopicFilter,
                onClearFilter = ::clearTopicFilter,
                onSelectPost = ::selectPost,
                containerRef = postsContainerRef,
                loadMoreRef = loadMoreRef,
            )
            repliesColumn(
                translator = translator,
                visualsFactory = visualsFactory,
                selectedPost = selectedPost,
                replies = replies,
                isLoading = isLoadingReplies,
                containerRef = repliesContainerRef,
                loadMoreRef = repliesLoadMoreRef,
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

private fun ChildrenBuilder.postsColumn(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    posts: List<TopicPosts>,
    selectedTopic: Topic?,
    selectedPost: QueryResult.Entry?,
    isLoading: Boolean,
    activeFilters: Map<String, Float>,
    onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    onClearFilter: (Topic) -> Unit,
    onSelectPost: (QueryResult.Entry) -> Unit,
    containerRef: Ref<HTMLDivElement>,
    loadMoreRef: Ref<HTMLDivElement>,
) = div {
    className = ClassName("topic-board__column topic-board__column--posts")
    ref = containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    val activeFilterTopic =
        posts.firstNotNullOfOrNull { topicPosts ->
            activeFilters[topicPosts.topic.title]?.let { topicPosts.topic to it }
        }
    div {
        className = ClassName("topic-board__column-header")
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.posts.title")
        }
        div {
            className = ClassName("topic-board__column-actions")
            if (activeFilterTopic != null) {
                val (filterTopic, filterScore) = activeFilterTopic
                val filterLabel =
                    translator.translate(
                        "connected.posts.filter.active",
                        mapOf("value" to formatSimilarity(filterScore)),
                    )
                val clearLabel = translator.translate("connected.posts.filter.clear")
                div {
                    className = ClassName("topic-board__filter-indicator")
                    button {
                        className = ClassName("topic-board__filter-reset")
                        ariaLabel = clearLabel
                        title = clearLabel
                        onClick = { onClearFilter(filterTopic) }
                        i {
                            className =
                                ClassName("fa fa-solid fa-arrows-up-to-line topic-board__filter-reset-icon")
                        }
                    }
                    span {
                        className = ClassName("topic-board__filter-value")
                        +filterLabel
                    }
                }
            }
        }
    }
    val highlightTopic = selectedTopic
    if (highlightTopic != null) {
        topicHighlightCard(translator, highlightTopic)
    }
    when {
        isLoading -> Unit
        posts.isEmpty() ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.empty")
            }
        else ->
            posts.forEach { topicPosts ->
                val filterScore = activeFilters[topicPosts.topic.title]
                val filterResult = filterEntriesByScore(topicPosts.entries, filterScore)
                filterResult.entries.forEach { entry ->
                    val isFilterEntry = filterResult.filteredEntryId == entry.id
                    postCard(
                        translator = translator,
                        visualsFactory = visualsFactory,
                        entry = entry,
                        topic = topicPosts.topic,
                        isFilterEntry = isFilterEntry,
                        onToggleFilter = onToggleFilter,
                        isSelected = selectedPost?.id == entry.id,
                        onSelect = { onSelectPost(entry) },
                    )
                }
            }
    }
    if (!isLoading && posts.isNotEmpty()) {
        div {
            ref = loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
}

private fun ChildrenBuilder.repliesColumn(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    selectedPost: QueryResult.Entry?,
    replies: ThreadReplies?,
    isLoading: Boolean,
    containerRef: Ref<HTMLDivElement>,
    loadMoreRef: Ref<HTMLDivElement>,
) = div {
    className = ClassName("topic-board__column topic-board__column--replies")
    ref = containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    div {
        className = ClassName("topic-board__column-header")
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.replies.title")
        }
    }
    when {
        selectedPost == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.empty")
            }
        isLoading && replies == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.loading")
            }
        else -> {
            replyOriginCard(translator, visualsFactory, selectedPost)
            val replyEntries = replies?.entries.orEmpty()
            if (replyEntries.isEmpty() && !isLoading) {
                div {
                    className = ClassName("topic-board__empty")
                    +translator.translate("connected.replies.none")
                }
            }
            replyEntries.forEach { reply ->
                replyCard(visualsFactory, reply)
            }
        }
    }
    if (!isLoading && replies != null && replies.entries.isNotEmpty()) {
        div {
            ref = loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
}

private fun ChildrenBuilder.replyCard(
    visualsFactory: IdentityVisualsFactory,
    reply: QueryResult.Entry,
) {
    val authorVisuals = visualsFactory.createFromGid(reply.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    div {
        key = reply.id
        className = ClassName("reply-card")
        div {
            className = ClassName("reply-card__author-row")
            img {
                className = ClassName("reply-card__avatar")
                src = authorVisuals.avatar
                alt = authorName
            }
            div {
                className = ClassName("reply-card__details")
                span {
                    className = ClassName("reply-card__author-name")
                    +authorName
                }
                span {
                    className = ClassName("reply-card__timestamp")
                    +formatTimestamp(reply.storedAt)
                }
            }
        }
        div {
            className = ClassName("reply-card__content")
            +reply.content
        }
    }
}

private fun ChildrenBuilder.replyOriginCard(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    origin: QueryResult.Entry,
) {
    val authorVisuals = visualsFactory.createFromGid(origin.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(origin.storedAt)
    div {
        key = origin.id
        className = ClassName("reply-origin")
        span {
            className = ClassName("reply-origin__badge")
            +translator.translate("connected.replies.source")
        }
        div {
            className = ClassName("reply-origin__author-row")
            img {
                className = ClassName("reply-origin__avatar")
                src = authorVisuals.avatar
                alt = authorName
            }
            div {
                className = ClassName("reply-origin__author-details")
                span {
                    className = ClassName("reply-origin__author-name")
                    +authorName
                }
                span {
                    className = ClassName("reply-origin__timestamp")
                    +timestamp
                }
            }
        }
        div {
            className = ClassName("reply-origin__content")
            +origin.content
        }
    }
}

private fun ChildrenBuilder.topicHighlightCard(
    translator: Translator,
    topic: Topic,
) {
    div {
        className = ClassName("topic-highlight")
        span {
            className = ClassName("topic-highlight__badge")
            +translator.translate("connected.posts.selectedTopic")
        }
        span {
            className = ClassName("topic-highlight__title")
            +topic.title
        }
    }
}

private fun ChildrenBuilder.postCard(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    entry: QueryResult.Entry,
    topic: Topic,
    isFilterEntry: Boolean,
    onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val authorVisuals = visualsFactory.createFromGid(entry.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(entry.storedAt)
    val similarity = formatSimilarity(entry.similarity)
    val cardClass =
        buildString {
            append("post-card")
            if (isFilterEntry) append(" post-card--dimmed")
            if (isSelected) append(" post-card--selected")
        }
    val filterLabel =
        if (isFilterEntry) {
            translator.translate("connected.posts.filter.clear")
        } else {
            translator.translate("connected.posts.filter.apply")
        }
    div {
        key = entry.id
        className = ClassName(cardClass)
        onClick = { onSelect() }
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
                    button {
                        className =
                            if (isFilterEntry) {
                                ClassName("post-card__filter post-card__filter--active")
                            } else {
                                ClassName("post-card__filter")
                            }
                        ariaLabel = filterLabel
                        title = filterLabel
                        onClick = { event: MouseEvent<*, *> ->
                            event.stopPropagation()
                            onToggleFilter(topic, entry)
                        }
                        i {
                            className = ClassName("fa fa-solid fa-arrows-up-to-line")
                        }
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
