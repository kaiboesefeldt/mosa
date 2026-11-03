package straightway.mosa.client

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.browser.window
import org.koin.core.context.GlobalContext
import kotlin.js.unsafeCast
import kotlin.js.console
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import straightway.mosa.client.assets.LogoResources
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.legal.LEGAL_DOCUMENT_IMPRINT
import straightway.mosa.client.legal.LEGAL_DOCUMENT_LICENSES
import straightway.mosa.client.legal.LEGAL_DOCUMENT_PRIVACY
import straightway.mosa.client.legal.LEGAL_DOCUMENT_TERMS
import straightway.mosa.client.legal.LegalComparisonSelection
import straightway.mosa.client.legal.LegalDialogState
import straightway.mosa.client.legal.LegalImprintService
import straightway.mosa.client.legal.LegalPrivacyPolicyService
import straightway.mosa.client.legal.LegalPrivacyPolicyDocument
import straightway.mosa.client.legal.MarkdownToHtmlRenderer
import straightway.mosa.client.legal.LegalRailMarker
import straightway.mosa.client.legal.LegalViewportIndicator
import straightway.mosa.client.legal.downloadLegalPdf
import straightway.mosa.client.legal.legalDialogOverlay
import straightway.mosa.client.legal.refreshComparisonBox
import straightway.mosa.client.legal.updateLegalRail
import straightway.mosa.client.ui.PasswordPromptOverlay
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLDivElement

internal val IdentityApp = FC<Props> {
    val appTranslator = GlobalContext.get().get<Translator>()
    val legalImprintService = useMemo(*identityAppNoDependencies) { GlobalContext.get().get<LegalImprintService>() }
    val legalPrivacyPolicyService = useMemo(*identityAppNoDependencies) { GlobalContext.get().get<LegalPrivacyPolicyService>() }
    val scope = useMemo(*identityAppNoDependencies) { MainScope() }
    val legalDocumentContentRef = useRef<HTMLDivElement>()
    val lastRenderedLegalHtmlRef = useRef("")
    val lastAutoScrolledComparisonRef = useRef("")
    val legalRequestVersion = useRef(0)
    var isLegalMenuVisible by useState(false)
    var activeLegalDocument by useState<String?>(null)
    var legalDocumentState by useState<LegalDialogState>(LegalDialogState.Idle)
    var isLegalPdfDownloading by useState(false)
    var activeComparison by useState<LegalComparisonSelection?>(null)
    var legalDiffMarkers by useState<List<LegalRailMarker>>(emptyList())
    var legalViewportIndicator by useState(LegalViewportIndicator(topPercent = 0.0, heightPercent = 100.0))
    val activeDocumentTitle =
        when (activeLegalDocument) {
            LEGAL_DOCUMENT_PRIVACY -> appTranslator.translate("legal.privacy.label")
            LEGAL_DOCUMENT_IMPRINT -> appTranslator.translate("legal.imprint.label")
            LEGAL_DOCUMENT_TERMS -> appTranslator.translate("legal.terms.label")
            LEGAL_DOCUMENT_LICENSES -> appTranslator.translate("legal.licenses.label")
            else -> null
        }
    val legalDialogTitle =
        when (activeLegalDocument) {
            LEGAL_DOCUMENT_PRIVACY -> activeDocumentTitle
            LEGAL_DOCUMENT_IMPRINT -> activeDocumentTitle
            LEGAL_DOCUMENT_TERMS -> activeDocumentTitle
            LEGAL_DOCUMENT_LICENSES -> activeDocumentTitle
            else -> null
        }

    useEffect(arrayOf(activeLegalDocument)) {
        if (
            activeLegalDocument != LEGAL_DOCUMENT_PRIVACY &&
            activeLegalDocument != LEGAL_DOCUMENT_IMPRINT &&
            activeLegalDocument != LEGAL_DOCUMENT_TERMS
            && activeLegalDocument != LEGAL_DOCUMENT_LICENSES
        ) {
            legalDocumentState = LegalDialogState.Idle
            isLegalPdfDownloading = false
            legalDocumentContentRef.current?.asDynamic()?.innerHTML = ""
            lastRenderedLegalHtmlRef.current = ""
            activeComparison = null
            legalDiffMarkers = emptyList()
            return@useEffect
        }

        if (legalDocumentState != LegalDialogState.Idle) {
            return@useEffect
        }

        legalRequestVersion.current = (legalRequestVersion.current ?: 0) + 1
        val requestVersion = legalRequestVersion.current
        legalDocumentState = LegalDialogState.Loading

        scope.launch {
            val result =
                runCatching {
                    when (activeLegalDocument) {
                        LEGAL_DOCUMENT_PRIVACY -> legalPrivacyPolicyService.loadPrivacyPolicyDocument()
                        LEGAL_DOCUMENT_IMPRINT -> legalImprintService.loadImprintDocument()
                        LEGAL_DOCUMENT_TERMS -> legalPrivacyPolicyService.loadTermsDocument()
                        LEGAL_DOCUMENT_LICENSES -> {
                            val response = window.fetch("/licenses/THIRD-PARTY-NOTICES.md").await()
                            check(response.ok) { "Failed to load third-party notices: ${response.status}" }
                            LegalPrivacyPolicyDocument(
                                html = MarkdownToHtmlRenderer().render(response.text().await()),
                                uncriticalTextByBlockId = emptyMap(),
                            )
                        }
                        else -> error("Unsupported legal document: $activeLegalDocument")
                    }
                }
            if (legalRequestVersion.current != requestVersion) {
                return@launch
            }
            result
                .onSuccess { document ->
                    legalDocumentState = LegalDialogState.Ready(document)
                }
                .onFailure {
                    console.error("Failed to load legal document", it)
                    val messageKey =
                        when (activeLegalDocument) {
                            LEGAL_DOCUMENT_IMPRINT -> "legal.imprint.load.error"
                            LEGAL_DOCUMENT_TERMS -> "legal.terms.load.error"
                            LEGAL_DOCUMENT_LICENSES -> "legal.licenses.load.error"
                            else -> "legal.privacy.load.error"
                        }
                    legalDocumentState = LegalDialogState.Error(appTranslator.translate(messageKey))
                }
        }
    }

    useEffect(arrayOf(legalDocumentState)) {
        val html =
            when (val state = legalDocumentState) {
                LegalDialogState.Idle -> ""
                LegalDialogState.Loading -> ""
                is LegalDialogState.Ready -> state.document.html
                is LegalDialogState.Error -> ""
            }
        val contentElement = legalDocumentContentRef.current
        val lastRenderedHtml = lastRenderedLegalHtmlRef.current ?: ""
        if (contentElement != null && html != lastRenderedHtml) {
            contentElement.asDynamic().innerHTML = html
            lastRenderedLegalHtmlRef.current = html
            activeComparison = null
            contentElement.updateLegalRail { markers, indicator ->
                legalDiffMarkers = markers
                legalViewportIndicator = indicator
            }
        }
    }

    useEffect(arrayOf(legalDocumentState, activeComparison, appTranslator, activeLegalDocument)) {
        if (activeLegalDocument != LEGAL_DOCUMENT_PRIVACY && activeLegalDocument != LEGAL_DOCUMENT_TERMS) {
            return@useEffect
        }
        val readyState = legalDocumentState as? LegalDialogState.Ready ?: return@useEffect
        val contentElement = legalDocumentContentRef.current ?: return@useEffect
        contentElement.refreshComparisonBox(
            selection = activeComparison,
            document = readyState.document,
            comparisonLabel = appTranslator.translate("legal.comparison.original"),
            missingComparisonText = appTranslator.translate("legal.comparison.original.missing"),
            lastAutoScrolledSelectionKey = lastAutoScrolledComparisonRef.current.orEmpty(),
            updateAutoScrolledSelectionKey = { lastAutoScrolledComparisonRef.current = it },
        )
    }

    useEffect(*identityAppNoDependencies) {
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
            IdentitySessionContent {}
        }
        div {
            className = ClassName("legal-fab")
            button {
                type = "button".unsafeCast<ButtonType>()
                className = ClassName("legal-fab__trigger")
                ariaLabel = appTranslator.translate("legal.button.label")
                title = appTranslator.translate("legal.button.label")
                onClick = {
                    isLegalMenuVisible = !isLegalMenuVisible
                }
                i {
                    className = ClassName("fa fa-solid fa-scale-balanced")
                }
                span {
                    className = ClassName("legal-fab__label")
                    +appTranslator.translate("legal.button.label")
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
                        +appTranslator.translate("legal.privacy.label")
                    }
                    a {
                        href = "#legal-imprint"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_IMPRINT
                            isLegalMenuVisible = false
                        }
                        +appTranslator.translate("legal.imprint.label")
                    }
                    a {
                        href = "#legal-terms"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_TERMS
                            isLegalMenuVisible = false
                        }
                        +appTranslator.translate("legal.terms.label")
                    }
                    a {
                        href = "#legal-licenses"
                        onClick = { event ->
                            event.preventDefault()
                            activeLegalDocument = LEGAL_DOCUMENT_LICENSES
                            isLegalMenuVisible = false
                        }
                        +appTranslator.translate("legal.licenses.label")
                    }
                }
            }
        }
    }

    if (activeLegalDocument != null) {
        legalDialogOverlay(
            translator = appTranslator,
            activeLegalDocument = activeLegalDocument,
            legalDialogTitle = legalDialogTitle,
            legalDocumentState = legalDocumentState,
            isLegalPdfDownloading = isLegalPdfDownloading,
            legalDocumentContentRef = legalDocumentContentRef,
            legalDiffMarkers = legalDiffMarkers,
            legalViewportIndicator = legalViewportIndicator,
            onClose = { activeLegalDocument = null },
            onLegalPdfDownload = clickHandler@{
                val contentElement = legalDocumentContentRef.current
                if (contentElement == null) return@clickHandler
                val fileNameKey =
                    when (activeLegalDocument) {
                        LEGAL_DOCUMENT_PRIVACY -> "legal.privacy.downloadPdf.filename"
                        LEGAL_DOCUMENT_TERMS -> "legal.terms.downloadPdf.filename"
                        else -> return@clickHandler
                    }
                isLegalPdfDownloading = true
                scope.launch {
                    runCatching {
                        downloadLegalPdf(
                            source = contentElement.unsafeCast<org.w3c.dom.HTMLDivElement>(),
                            logoUrl = LogoResources.dataUrl,
                            fileName = appTranslator.translate(fileNameKey),
                        )
                    }.onFailure {
                        console.error("Failed to download legal document PDF", it)
                    }
                    isLegalPdfDownloading = false
                }
            },
            onComparisonToggle = { selection ->
                activeComparison =
                    if (activeComparison?.groupId == selection.groupId && activeComparison?.blockId == selection.blockId) {
                        null
                    } else {
                        LegalComparisonSelection(groupId = selection.groupId, blockId = selection.blockId)
                    }
            },
            onPrivacyScroll = scrollHandler@{
                val contentElement = legalDocumentContentRef.current ?: return@scrollHandler
                contentElement.updateLegalRail { markers, indicator ->
                    legalDiffMarkers = markers
                    legalViewportIndicator = indicator
                }
            },
        )
    }
}

private val identityAppNoDependencies: Array<dynamic> = emptyArray()
