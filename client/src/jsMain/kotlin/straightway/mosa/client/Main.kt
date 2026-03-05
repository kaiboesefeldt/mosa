package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.js.unsafeCast
import kotlin.js.console
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import straightway.mosa.client.assets.FontAwesomeResources
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.legal.LegalPrivacyPolicyService
import straightway.mosa.client.legal.LegalRailMarker
import straightway.mosa.client.legal.LegalMarkerSeverity
import straightway.mosa.client.legal.LegalViewportIndicator
import straightway.mosa.client.legal.computeLegalViewportIndicator
import straightway.mosa.client.legal.MarkdownToHtmlRenderer
import straightway.mosa.client.legal.quantizeRailMarkers
import straightway.mosa.client.ui.IdentityConnectedView
import straightway.mosa.client.ui.IdentityProfileView
import straightway.mosa.client.ui.applyFrom
import straightway.mosa.client.ui.IdentityScreen
import straightway.mosa.client.ui.PasswordPromptOverlay
import straightway.mosa.client.ui.useIdentitySession
import web.cssom.ClassName
import web.dom.Element
import web.html.ButtonType
import web.html.HTMLDivElement
import web.dom.Element as WebElement

private val comparisonMarkdownRenderer = MarkdownToHtmlRenderer()

@ExperimentalWasmJsInterop
fun main() {
    FontAwesomeResources.loadStyles()
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}

private val IdentityApp = FC<Props> {
    val translator = GlobalContext.get().get<Translator>()
    val legalPrivacyPolicyService = useMemo(*mainNoDependencies) { GlobalContext.get().get<LegalPrivacyPolicyService>() }
    val scope = useMemo(*mainNoDependencies) { MainScope() }
    val legalDocumentContentRef = useRef<HTMLDivElement>()
    val lastRenderedLegalHtmlRef = useRef("")
    val lastAutoScrolledComparisonRef = useRef("")
    val legalRequestVersion = useRef(0)
    var isLegalMenuVisible by useState(false)
    var activeLegalDocument by useState<String?>(null)
    var privacyDocumentState by useState<PrivacyDocumentState>(PrivacyDocumentState.Idle)
    var activeComparison by useState<LegalComparisonSelection?>(null)
    var legalDiffMarkers by useState<List<LegalRailMarker>>(emptyList())
    var legalViewportIndicator by useState(LegalViewportIndicator(topPercent = 0.0, heightPercent = 100.0))
    val activeDocumentTitle =
        when (activeLegalDocument) {
            LEGAL_DOCUMENT_PRIVACY -> translator.translate("legal.privacy.label")
            LEGAL_DOCUMENT_IMPRINT -> translator.translate("legal.imprint.label")
            LEGAL_DOCUMENT_TERMS -> translator.translate("legal.terms.label")
            else -> null
        }
    val legalDialogTitle =
        when (activeLegalDocument) {
            LEGAL_DOCUMENT_PRIVACY -> activeDocumentTitle
            LEGAL_DOCUMENT_IMPRINT,
            LEGAL_DOCUMENT_TERMS,
            -> "$activeDocumentTitle (Dummy)"
            else -> null
        }

    useEffect(arrayOf(activeLegalDocument)) {
        if (activeLegalDocument != LEGAL_DOCUMENT_PRIVACY) {
            privacyDocumentState = PrivacyDocumentState.Idle
            legalDocumentContentRef.current?.asDynamic()?.innerHTML = ""
            lastRenderedLegalHtmlRef.current = ""
            activeComparison = null
            legalDiffMarkers = emptyList()
            return@useEffect
        }

        if (privacyDocumentState != PrivacyDocumentState.Idle) {
            return@useEffect
        }

        legalRequestVersion.current = (legalRequestVersion.current ?: 0) + 1
        val requestVersion = legalRequestVersion.current
        privacyDocumentState = PrivacyDocumentState.Loading

        scope.launch {
            val result = runCatching { legalPrivacyPolicyService.loadPrivacyPolicyDocument() }
            if (legalRequestVersion.current != requestVersion) {
                return@launch
            }
            result
                .onSuccess { document ->
                    privacyDocumentState = PrivacyDocumentState.Ready(document)
                }
                .onFailure {
                    console.error("Failed to load privacy policy", it)
                    privacyDocumentState = PrivacyDocumentState.Error(translator.translate("legal.privacy.load.error"))
                }
        }
    }

    useEffect(arrayOf(privacyDocumentState)) {
        val html =
            when (val state = privacyDocumentState) {
                PrivacyDocumentState.Idle -> ""
                PrivacyDocumentState.Loading -> ""
                is PrivacyDocumentState.Ready -> state.document.html
                is PrivacyDocumentState.Error -> ""
            }
        val contentElement = legalDocumentContentRef.current
        val lastRenderedHtml = lastRenderedLegalHtmlRef.current ?: ""
        if (contentElement != null && html != lastRenderedHtml) {
            contentElement.asDynamic().innerHTML = html
            lastRenderedLegalHtmlRef.current = html
            activeComparison = null
            updateLegalRail(contentElement) { markers, indicator ->
                legalDiffMarkers = markers
                legalViewportIndicator = indicator
            }
        }
    }

    useEffect(arrayOf(privacyDocumentState, activeComparison, translator)) {
        if (activeLegalDocument != LEGAL_DOCUMENT_PRIVACY) {
            return@useEffect
        }
        val readyState = privacyDocumentState as? PrivacyDocumentState.Ready ?: return@useEffect
        val contentElement = legalDocumentContentRef.current ?: return@useEffect

        fun refreshComparisonBox() {
            removeExistingComparisonBoxes(contentElement)
            val selection =
                activeComparison ?: run {
                    lastAutoScrolledComparisonRef.current = ""
                    return
                }
            val uncriticalText =
                readyState.document.uncriticalTextByBlockId[selection.blockId]
                    ?.ifBlank { translator.translate("legal.comparison.original.missing") }
                    ?: translator.translate("legal.comparison.original.missing")
            val groupElement = findComparisonGroupElement(contentElement, selection.groupId) ?: return
            val box = buildComparisonBox(uncriticalText, translator.translate("legal.comparison.original"))
            mountComparisonBox(contentElement, box)
            val selectionKey = "${selection.groupId}|${selection.blockId}"
            if (lastAutoScrolledComparisonRef.current != selectionKey) {
                scrollComparisonGroupIntoView(contentElement, groupElement)
                lastAutoScrolledComparisonRef.current = selectionKey
            }
        }

        refreshComparisonBox()
    }

    useEffect(*mainNoDependencies) {
        try {
            awaitCancellation()
        } finally {
            scope.cancel()
        }
    }

    PasswordPromptOverlay {}
    div {
        className = ClassName("app-shell")
        div {
            className = ClassName("app-shell__content")
            when (val screen = useIdentitySession()) {
                IdentityScreen.Loading -> Unit
                is IdentityScreen.Connected ->
                    IdentityConnectedView {
                        identityName = screen.identityName
                        profile = screen.profile
                    }

                is IdentityScreen.Profile ->
                    IdentityProfileView {
                        applyFrom(screen)
                    }
            }
        }
        div {
            className = ClassName("legal-fab")
            button {
                type = "button".unsafeCast<ButtonType>()
                className = ClassName("legal-fab__trigger")
                ariaLabel = translator.translate("legal.button.label")
                title = translator.translate("legal.button.label")
                onClick = {
                    isLegalMenuVisible = !isLegalMenuVisible
                }
                i {
                    className = ClassName("fa fa-solid fa-scale-balanced")
                }
                span {
                    className = ClassName("legal-fab__label")
                    +translator.translate("legal.button.label")
                }
            }
            if (isLegalMenuVisible) {
                div {
                    className = ClassName("legal-fab__menu")
                    a {
                        href = "#legal-privacy"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_PRIVACY
                            isLegalMenuVisible = false
                        }
                        +translator.translate("legal.privacy.label")
                    }
                    a {
                        href = "#legal-imprint"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_IMPRINT
                            isLegalMenuVisible = false
                        }
                        +translator.translate("legal.imprint.label")
                    }
                    a {
                        href = "#legal-terms"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_TERMS
                            isLegalMenuVisible = false
                        }
                        +translator.translate("legal.terms.label")
                    }
                }
            }
        }
    }

    if (activeLegalDocument != null) {
        div {
            className = ClassName("legal-overlay")
            onClick = { event ->
                if (event.target == event.currentTarget) {
                    activeLegalDocument = null
                }
            }
            section {
                className = ClassName("legal-dialog")
                div {
                    className = ClassName("legal-dialog__header")
                    h2 {
                        className = ClassName("legal-dialog__title")
                        +(legalDialogTitle ?: "")
                    }
                    button {
                        type = "button".unsafeCast<ButtonType>()
                        className = ClassName("legal-dialog__close")
                        ariaLabel = translator.translate("legal.close.label")
                        title = translator.translate("legal.close.label")
                        onClick = {
                            activeLegalDocument = null
                        }
                        +"x"
                    }
                }
                when (activeLegalDocument) {
                    LEGAL_DOCUMENT_PRIVACY -> {
                        when {
                            privacyDocumentState is PrivacyDocumentState.Loading -> {
                                p { +translator.translate("legal.privacy.loading") }
                            }

                            privacyDocumentState is PrivacyDocumentState.Error -> {
                                p { +((privacyDocumentState as PrivacyDocumentState.Error).text) }
                            }

                            privacyDocumentState is PrivacyDocumentState.Ready -> {
                                div {
                                    className = ClassName("legal-dialog__content-wrap")
                                    div {
                                        className = ClassName("legal-dialog__content")
                                        ref = legalDocumentContentRef
                                        onMouseDown = mouseDownHandler@{ event ->
                                            val marker = resolveClickableMarker(event.target)
                                            if (marker == null) return@mouseDownHandler
                                            val blockId = resolveBlockId(marker)
                                            if (blockId == null) return@mouseDownHandler
                                            val groupId =
                                                (marker.closest("[data-ct-group-id]") as? Element)?.getAttribute("data-ct-group-id") ?: blockId
                                            event.preventDefault()
                                            activeComparison =
                                                if (activeComparison?.groupId == groupId && activeComparison?.blockId == blockId) {
                                                    null
                                                } else {
                                                    LegalComparisonSelection(groupId = groupId, blockId = blockId)
                                                }
                                        }
                                        onScroll = scrollHandler@{
                                            val contentElement = legalDocumentContentRef.current ?: return@scrollHandler
                                            updateLegalRail(contentElement) { markers, indicator ->
                                                legalDiffMarkers = markers
                                                legalViewportIndicator = indicator
                                            }
                                        }
                                    }
                                    div {
                                        className = ClassName("legal-dialog__scroll-marker-rail")
                                        legalDiffMarkers.forEachIndexed { index, marker ->
                                            val severity =
                                                when (marker.severity) {
                                                    LegalMarkerSeverity.RED -> LEGAL_MARKER_RED
                                                    LegalMarkerSeverity.YELLOW -> LEGAL_MARKER_YELLOW
                                                }
                                            div {
                                                className =
                                                    ClassName(
                                                        "legal-dialog__scroll-marker legal-dialog__scroll-marker--$severity",
                                                    )
                                                this.asDynamic().key = "legal-scroll-marker-$index-$severity-${marker.topPercent}"
                                                val markerStyle: dynamic = js("({})")
                                                markerStyle.top = "${marker.topPercent}%"
                                                this.asDynamic().style = markerStyle
                                            }
                                        }
                                        div {
                                            className = ClassName("legal-dialog__viewport-indicator")
                                            val indicatorStyle: dynamic = js("({})")
                                            indicatorStyle.top = "${legalViewportIndicator.topPercent}%"
                                            indicatorStyle.height = "${legalViewportIndicator.heightPercent}%"
                                            this.asDynamic().style = indicatorStyle
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        p {
                            +"Dies ist ein Platzhaltertext. Das finale Rechtsdokument wird später mit ClearTerms ausgeliefert."
                        }
                        p {
                            +"Aktuell dient dieser Inhalt nur der UI-Integration und ist rechtlich nicht verbindlich."
                        }
                        p {
                            +"Verantwortlich ist der Heimat-Pod, von dem diese App geladen wurde."
                        }
                    }
                }
            }
        }
    }
}

private const val LEGAL_DOCUMENT_PRIVACY = "privacy"
private const val LEGAL_DOCUMENT_IMPRINT = "imprint"
private const val LEGAL_DOCUMENT_TERMS = "terms"
private const val LEGAL_MARKER_RED = "red"
private const val LEGAL_MARKER_YELLOW = "yellow"
private val mainNoDependencies: Array<dynamic> = emptyArray()

private fun collectLegalScrollMarkers(contentElement: HTMLDivElement): List<LegalRailMarker> {
    val totalHeight = contentElement.scrollHeight.toDouble()
    if (totalHeight <= 0.0) {
        return emptyList()
    }
    val contentRect = contentElement.getBoundingClientRect()
    val nodes = contentElement.querySelectorAll(".legal-ct-bg-yellow, .legal-ct-bg-red")
    val markers = mutableListOf<LegalRailMarker>()
    val nodeCount = nodes.length
    for (index in 0 until nodeCount) {
        val node = nodes.item(index) ?: continue
        val severity =
            when {
                node.className.toString().contains("legal-ct-bg-red") -> LegalMarkerSeverity.RED
                node.className.toString().contains("legal-ct-bg-yellow") -> LegalMarkerSeverity.YELLOW
                else -> null
            } ?: continue
        val nodeTop = node.getBoundingClientRect().top - contentRect.top + contentElement.scrollTop
        val topPercent = ((nodeTop / totalHeight) * 100.0).coerceIn(0.0, 100.0)
        markers += LegalRailMarker(topPercent = topPercent, severity = severity)
    }
    return markers
}

private fun updateLegalRail(
    contentElement: HTMLDivElement,
    apply: (List<LegalRailMarker>, LegalViewportIndicator) -> Unit,
) {
    val rawMarkers = collectLegalScrollMarkers(contentElement)
    val markers = quantizeRailMarkers(rawMarkers, contentElement.clientHeight)
    val indicator =
        computeLegalViewportIndicator(
            scrollTop = contentElement.scrollTop,
            scrollHeight = contentElement.scrollHeight.toDouble(),
            clientHeight = contentElement.clientHeight.toDouble(),
        )
    apply(markers, indicator)
}

private sealed interface PrivacyDocumentState {
    data object Idle : PrivacyDocumentState

    data object Loading : PrivacyDocumentState

    data class Ready(
        val document: straightway.mosa.client.legal.LegalPrivacyPolicyDocument,
    ) : PrivacyDocumentState

    data class Error(
        val text: String,
    ) : PrivacyDocumentState
}

private data class LegalComparisonSelection(
    val groupId: String,
    val blockId: String,
)

private fun findComparisonGroupElement(
    contentElement: HTMLDivElement,
    groupId: String,
): Element? {
    val groups = contentElement.querySelectorAll("[data-ct-group-id]")
    for (index in 0 until groups.length) {
        val candidate = groups.item(index) as? Element ?: continue
        if (candidate.getAttribute("data-ct-group-id") == groupId) {
            return candidate
        }
    }
    return null
}

private fun removeExistingComparisonBoxes(contentElement: HTMLDivElement) {
    val existing = contentElement.parentElement?.querySelectorAll(".legal-ct-comparison-box") ?: return
    for (index in existing.length - 1 downTo 0) {
        val node = existing.item(index) ?: continue
        node.parentNode?.removeChild(node)
    }
}

private fun buildComparisonBox(
    uncriticalText: String,
    label: String,
): WebElement {
    val box = document.createElement("div")
    box.className = "legal-ct-comparison-box"
    val boxLabel = document.createElement("div")
    boxLabel.className = "legal-ct-comparison-box__label"
    boxLabel.textContent = label
    val boxText = document.createElement("div")
    boxText.className = "legal-ct-comparison-box__text"
    boxText.asDynamic().innerHTML = comparisonMarkdownRenderer.render(uncriticalText)
    box.appendChild(boxLabel)
    box.appendChild(boxText)
    return box.unsafeCast<WebElement>()
}

private fun scrollComparisonGroupIntoView(
    contentElement: HTMLDivElement,
    groupElement: Element,
) {
    val contentRect = contentElement.getBoundingClientRect()
    val groupRect = groupElement.getBoundingClientRect()
    val margin = 12.0
    when {
        groupRect.bottom > contentRect.bottom - margin -> {
            val delta = groupRect.bottom - contentRect.bottom + margin
            contentElement.scrollTop += delta
        }
        groupRect.top < contentRect.top + margin -> {
            val delta = contentRect.top - groupRect.top + margin
            contentElement.scrollTop -= delta
        }
    }
}

private fun mountComparisonBox(
    contentElement: HTMLDivElement,
    box: WebElement,
) {
    val wrapElement = contentElement.parentElement ?: return
    wrapElement.appendChild(box)
}


private fun resolveClickableMarker(target: dynamic): Element? {
    var node = target
    while (node != null) {
        val classList = node.classList
        if (classList != null) {
            val isYellow = classList.contains("legal-ct-bg-yellow") as? Boolean ?: false
            val isRed = classList.contains("legal-ct-bg-red") as? Boolean ?: false
            if (isYellow || isRed) {
                return node.unsafeCast<Element>()
            }
        }
        node = node.parentNode
    }
    return null
}

private fun resolveBlockId(marker: Element): String? {
    val direct = marker.getAttribute("data-ct-block-id")
    if (!direct.isNullOrBlank()) {
        return direct
    }
    val group = marker.closest("[data-ct-block-ids]") as? Element ?: return null
    val groupIds = group.getAttribute("data-ct-block-ids") ?: return null
    return groupIds
        .split(' ')
        .map { value -> value.trim() }
        .firstOrNull { value -> value.isNotEmpty() }
}
