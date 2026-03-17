package straightway.mosa.client.ui

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.unsafeCast
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.events.ChangeEvent
import react.dom.events.KeyboardEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useMemo
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.PasswordPromptController
import straightway.mosa.client.identity.PasswordPromptKind
import straightway.mosa.client.identity.PasswordPromptState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.InputType

val PasswordPromptOverlay = FC<Props> {
    val translator = PasswordPromptInjector.get<Translator>()
    val scope = useMemo(*passwordPromptNoDependencies) { MainScope() }
    var promptState by useState<PasswordPromptState?>(null)
    var password by useState("")
    var confirmation by useState("")
    var isRevealed by useState(false)

    useEffect(*passwordPromptNoDependencies) {
        val job =
            scope.launch {
                PasswordPromptController.state.collect { state ->
                    promptState = state
                }
            }
        try {
            awaitCancellation()
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    val state = promptState
    val passwordsMatch = password.isNotEmpty() && confirmation.isNotEmpty() && password == confirmation

    useEffect(state) {
        if (state != null) {
            password = ""
            confirmation = ""
            isRevealed = false
        }
    }

    if (state == null) return@FC

    val titleKey =
        when (state.kind) {
            PasswordPromptKind.Create -> "passwordPrompt.title.create"
            PasswordPromptKind.Enter -> "passwordPrompt.title.enter"
        }
    val passwordLabel = translator.translate("passwordPrompt.label.password")
    val confirmationLabel = translator.translate("passwordPrompt.label.confirm")
    val mismatchMessage = translator.translate("passwordPrompt.mismatch")
    val okLabel = translator.translate("passwordPrompt.action.ok")
    val cancelLabel = translator.translate("passwordPrompt.action.cancel")
    val toggleLabelKey = if (isRevealed) "passwordPrompt.hide" else "passwordPrompt.show"
    val toggleLabel = translator.translate(toggleLabelKey)

    val resolve: (String?) -> Unit = { value ->
        if (value == null) {
            PasswordPromptController.cancel(state)
        } else {
            PasswordPromptController.confirm(state, value)
        }
    }

    div {
        className = ClassName("password-popover__backdrop")
        onKeyDown = { event: KeyboardEvent<*> ->
            if (event.key == "Escape") {
                resolve(null)
            }
        }
        onClick = { event ->
            if (event.target == event.currentTarget) {
                resolve(null)
            }
        }
        div {
            className = ClassName("password-popover")
            span {
                className = ClassName("password-popover__title")
                +translator.translate(titleKey)
            }
            form {
                className = ClassName("password-popover__form")
                onSubmit = { event ->
                    event.preventDefault()
                    if (passwordsMatch) {
                        resolve(password)
                    }
                }
                label {
                    className = ClassName("password-popover__field")
                    span {
                        className = ClassName("password-popover__label")
                        +passwordLabel
                    }
                    div {
                        className = ClassName("password-popover__input")
                        input {
                            type = (if (isRevealed) "text" else "password").unsafeCast<InputType>()
                            value = password
                            autoFocus = true
                            placeholder = passwordLabel
                            onChange = { event: ChangeEvent<HTMLInputElement, HTMLInputElement> ->
                                password = event.currentTarget.value
                            }
                        }
                        button {
                            type = "button".unsafeCast<ButtonType>()
                            className = ClassName("button button--icon password-popover__toggle")
                            ariaLabel = toggleLabel
                            tabIndex = -1
                            title = toggleLabel
                            onClick = { event ->
                                event.preventDefault()
                                isRevealed = !isRevealed
                            }
                            span {
                                className = ClassName(if (isRevealed) "fa fa-solid fa-eye-slash" else "fa fa-solid fa-eye")
                            }
                        }
                    }
                }
                label {
                    className = ClassName("password-popover__field")
                    span {
                        className = ClassName("password-popover__label")
                        +confirmationLabel
                    }
                    input {
                        type = (if (isRevealed) "text" else "password").unsafeCast<InputType>()
                        value = confirmation
                        placeholder = confirmationLabel
                        onChange = { event: ChangeEvent<HTMLInputElement, HTMLInputElement> ->
                            confirmation = event.currentTarget.value
                        }
                    }
                }
                div {
                    className = ClassName("password-popover__hint-slot")
                    if (!passwordsMatch && (password.isNotEmpty() || confirmation.isNotEmpty())) {
                        span {
                            className = ClassName("password-popover__hint")
                            +mismatchMessage
                        }
                    }
                }
                div {
                    className = ClassName("password-popover__actions")
                    button {
                        type = "button".unsafeCast<ButtonType>()
                        className = ClassName("button button--secondary")
                        onClick = {
                            resolve(null)
                        }
                        +cancelLabel
                    }
                    button {
                        type = "submit".unsafeCast<ButtonType>()
                        className = ClassName("button button--primary")
                        disabled = !passwordsMatch
                        +okLabel
                    }
                }
            }
        }
    }
}

private object PasswordPromptInjector : KoinComponent

private val passwordPromptNoDependencies: Array<dynamic> = emptyArray()
