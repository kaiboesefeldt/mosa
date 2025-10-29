package straightway.mosa.client.ui

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import straightway.mosa.client.i18n.Translator

external interface IdentityLandingViewProps : Props {
    var identityName: String
    var isBusy: Boolean
    var errorText: String?
    var canConnect: Boolean
    var onConnect: () -> Unit
    var onGenerateNew: () -> Unit
}

val IdentityLandingView = FC<IdentityLandingViewProps> { props ->
    val translator = LandingViewInjector.get<Translator>()
    div {
        className = ClassName("landing-overlay")
        div {
            className = ClassName("landing-dialog")
            div {
                className = ClassName("landing-dialog__header")
                h1 {
                    className = ClassName("landing-dialog__title")
                    +translator.translate("landing.title")
                }
                p {
                    className = ClassName("landing-dialog__description")
                    +translator.translate("landing.description")
                }
            }
            div {
                className = ClassName("landing-dialog__identity")
                span {
                    className = ClassName("landing-dialog__identity-heading")
                    +translator.translate("landing.identityLabel")
                }
                p {
                    className = ClassName("landing-dialog__identity-value")
                    +props.identityName
                }
            }
            props.errorText?.let { message ->
                div {
                    className = ClassName("landing-dialog__error")
                    +message
                }
            }
            div {
                className = ClassName("landing-dialog__actions")
                button {
                    className = ClassName("button button--primary button--primary-large")
                    disabled = props.isBusy || !props.canConnect
                    ariaLabel = translator.translate("button.connect.aria")
                    onClick = { props.onConnect() }
                    +(if (props.isBusy) {
                        translator.translate("button.connect.busy")
                    } else {
                        translator.translate("button.connect.label")
                    })
                }
                button {
                    className = ClassName("button button--subtle")
                    disabled = props.isBusy
                    ariaLabel = translator.translate("button.generateIdentity.aria")
                    onClick = { props.onGenerateNew() }
                    +(if (props.isBusy) {
                        translator.translate("button.generateIdentity.busy")
                    } else {
                        translator.translate("button.generateIdentity.label")
                    })
                }
            }
        }
    }
}

private object LandingViewInjector : KoinComponent
