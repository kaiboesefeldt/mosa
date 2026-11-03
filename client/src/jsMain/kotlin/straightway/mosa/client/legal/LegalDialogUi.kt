package straightway.mosa.client.legal

import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.RefObject
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.section
import react.dom.html.ReactHTML.span
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLDivElement

internal fun ChildrenBuilder.legalDialogOverlay(
    translator: Translator,
    activeLegalDocument: String?,
    legalDialogTitle: String?,
    legalDocumentState: LegalDialogState,
    isLegalPdfDownloading: Boolean,
    legalDocumentContentRef: RefObject<HTMLDivElement>,
    legalDiffMarkers: List<LegalRailMarker>,
    legalViewportIndicator: LegalViewportIndicator,
    onClose: () -> Unit,
    onLegalPdfDownload: () -> Unit,
    onComparisonToggle: (LegalComparisonSelection) -> Unit,
    onPrivacyScroll: () -> Unit,
) {
    div {
        className = ClassName("legal-overlay")
        onClick = { event: MouseEvent<*, *> ->
            if (event.target == event.currentTarget) {
                onClose()
            }
        }
        section {
            className = ClassName("legal-dialog")
            legalDialogHeader(
                translator = translator,
                activeLegalDocument = activeLegalDocument,
                legalDialogTitle = legalDialogTitle,
                legalDocumentState = legalDocumentState,
                isLegalPdfDownloading = isLegalPdfDownloading,
                onClose = onClose,
                onLegalPdfDownload = onLegalPdfDownload,
            )
            when (activeLegalDocument) {
                LEGAL_DOCUMENT_PRIVACY ->
                    privacyDialogContent(
                        translator = translator,
                        state = legalDocumentState,
                    contentRef = legalDocumentContentRef,
                    legalDiffMarkers = legalDiffMarkers,
                    legalViewportIndicator = legalViewportIndicator,
                    onComparisonToggle = onComparisonToggle,
                    onPrivacyScrollAction = onPrivacyScroll,
                )

                LEGAL_DOCUMENT_TERMS ->
                    staticDialogContent(
                        state = legalDocumentState,
                        contentRef = legalDocumentContentRef,
                        loadingText = translator.translate("legal.terms.loading"),
                    )

                else ->
                    staticDialogContent(
                        state = legalDocumentState,
                        contentRef = legalDocumentContentRef,
                        loadingText = translator.translate("legal.imprint.loading"),
                    )
            }
        }
    }
}

private fun ChildrenBuilder.legalDialogHeader(
    translator: Translator,
    activeLegalDocument: String?,
    legalDialogTitle: String?,
    legalDocumentState: LegalDialogState,
    isLegalPdfDownloading: Boolean,
    onClose: () -> Unit,
    onLegalPdfDownload: () -> Unit,
) {
    div {
        className = ClassName("legal-dialog__header")
        h2 {
            className = ClassName("legal-dialog__title")
            +(legalDialogTitle ?: "")
        }
        val downloadLabelKey =
            when (activeLegalDocument) {
                LEGAL_DOCUMENT_PRIVACY -> "legal.privacy.downloadPdf.label"
                LEGAL_DOCUMENT_TERMS -> "legal.terms.downloadPdf.label"
                else -> null
            }
        if (downloadLabelKey != null) {
            val downloadProgressKey =
                if (activeLegalDocument == LEGAL_DOCUMENT_PRIVACY) {
                    "legal.privacy.downloadPdf.inProgress"
                } else {
                    "legal.terms.downloadPdf.inProgress"
                }
            val downloadLabel = translator.translate(downloadLabelKey)
            if (isLegalPdfDownloading) {
                span {
                    className = ClassName("legal-dialog__download-status")
                    this.asDynamic()["ariaLive"] = "polite"
                    i {
                        className = ClassName("fa fa-solid fa-spinner fa-spin")
                    }
                    span {
                        +translator.translate(downloadProgressKey)
                    }
                }
            }
            button {
                type = "button".unsafeCast<ButtonType>()
                className = ClassName("legal-dialog__download")
                ariaLabel = downloadLabel
                title = downloadLabel
                disabled = legalDocumentState !is LegalDialogState.Ready || isLegalPdfDownloading
                onClick = {
                    onLegalPdfDownload()
                }
                i {
                    className = ClassName("fa fa-solid fa-download")
                }
            }
        }
        button {
            type = "button".unsafeCast<ButtonType>()
            className = ClassName("legal-dialog__close")
            ariaLabel = translator.translate("legal.close.label")
            title = translator.translate("legal.close.label")
            onClick = {
                onClose()
            }
            +"X"
        }
    }
}

private fun ChildrenBuilder.privacyDialogContent(
    translator: Translator,
    state: LegalDialogState,
    contentRef: RefObject<HTMLDivElement>,
    legalDiffMarkers: List<LegalRailMarker>,
    legalViewportIndicator: LegalViewportIndicator,
    onComparisonToggle: (LegalComparisonSelection) -> Unit,
    onPrivacyScrollAction: () -> Unit,
) {
    when (state) {
        LegalDialogState.Idle -> Unit
        LegalDialogState.Loading -> p { +translator.translate("legal.privacy.loading") }
        is LegalDialogState.Error -> p { +(state.text) }
        is LegalDialogState.Ready -> {
            div {
                className = ClassName("legal-dialog__content-wrap")
                div {
                    className = ClassName("legal-dialog__content")
                    ref = contentRef
                    onMouseDown = mouseDownHandler@{ event ->
                        val marker = resolveClickableMarker(event.target) ?: return@mouseDownHandler
                        val blockId = resolveBlockId(marker) ?: return@mouseDownHandler
                        val groupId = marker.closest("[data-ct-group-id]")?.getAttribute("data-ct-group-id") ?: blockId
                        event.preventDefault()
                        onComparisonToggle(LegalComparisonSelection(groupId = groupId, blockId = blockId))
                    }
                    onScroll = {
                        onPrivacyScrollAction()
                    }
                }
                legalScrollMarkerRail(legalDiffMarkers, legalViewportIndicator)
            }
        }
    }
}

private fun ChildrenBuilder.staticDialogContent(
    state: LegalDialogState,
    contentRef: RefObject<HTMLDivElement>,
    loadingText: String,
) {
    when (state) {
        LegalDialogState.Idle -> Unit
        LegalDialogState.Loading -> p { +loadingText }
        is LegalDialogState.Error -> p { +(state.text) }
        is LegalDialogState.Ready ->
            div {
                className = ClassName("legal-dialog__content-wrap")
                div {
                    className = ClassName("legal-dialog__content")
                    ref = contentRef
                }
            }
    }
}

private fun ChildrenBuilder.legalScrollMarkerRail(
    legalDiffMarkers: List<LegalRailMarker>,
    legalViewportIndicator: LegalViewportIndicator,
) {
    div {
        className = ClassName("legal-dialog__scroll-marker-rail")
        legalDiffMarkers.forEachIndexed { index, marker ->
            div {
                val severity =
                    when (marker.severity) {
                        LegalMarkerSeverity.RED -> "red"
                        LegalMarkerSeverity.YELLOW -> "yellow"
                    }
                className = ClassName("legal-dialog__scroll-marker legal-dialog__scroll-marker--$severity")
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
