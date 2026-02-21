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
import straightway.mosa.client.ui.IdentityConnectedView
import straightway.mosa.client.ui.IdentityProfileView
import straightway.mosa.client.ui.applyFrom
import straightway.mosa.client.ui.IdentityScreen
import straightway.mosa.client.ui.PasswordPromptOverlay
import straightway.mosa.client.ui.useIdentitySession
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLDivElement
import web.dom.Element as WebElement

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
    val legalRequestVersion = useRef(0)
    var isLegalMenuVisible by useState(false)
    var activeLegalDocument by useState<String?>(null)
    var privacyDocumentState by useState<PrivacyDocumentState>(PrivacyDocumentState.Idle)
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
            return@useEffect
        }

        if (privacyDocumentState != PrivacyDocumentState.Idle) {
            return@useEffect
        }

        legalRequestVersion.current = (legalRequestVersion.current ?: 0) + 1
        val requestVersion = legalRequestVersion.current
        privacyDocumentState = PrivacyDocumentState.Loading

        scope.launch {
            val result = runCatching { legalPrivacyPolicyService.loadPrivacyPolicyHtml() }
            if (legalRequestVersion.current != requestVersion) {
                return@launch
            }
            result
                .onSuccess { html ->
                    privacyDocumentState = PrivacyDocumentState.Ready(html)
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
                is PrivacyDocumentState.Ready -> state.html
                is PrivacyDocumentState.Error -> ""
            }
        legalDocumentContentRef.current?.asDynamic()?.innerHTML = html
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
                h2 {
                    className = ClassName("legal-dialog__title")
                    +(legalDialogTitle ?: "")
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
                                    className = ClassName("legal-dialog__content")
                                    ref = legalDocumentContentRef
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
                a {
                    className = ClassName("legal-dialog__close")
                    href = "#close-legal"
                    onClick = { event ->
                        event.preventDefault()
                        activeLegalDocument = null
                    }
                    +translator.translate("legal.close.label")
                }
            }
        }
    }
}

private const val LEGAL_DOCUMENT_PRIVACY = "privacy"
private const val LEGAL_DOCUMENT_IMPRINT = "imprint"
private const val LEGAL_DOCUMENT_TERMS = "terms"
private val mainNoDependencies: Array<dynamic> = emptyArray()

private sealed interface PrivacyDocumentState {
    data object Idle : PrivacyDocumentState

    data object Loading : PrivacyDocumentState

    data class Ready(
        val html: String,
    ) : PrivacyDocumentState

    data class Error(
        val text: String,
    ) : PrivacyDocumentState
}
