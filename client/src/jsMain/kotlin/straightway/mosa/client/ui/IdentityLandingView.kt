package straightway.mosa.client.ui

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import web.dom.ElementId
import straightway.mosa.client.i18n.Translator

external interface IdentityLandingViewProps : Props {
    var identityName: String
    var pods: List<String>
    var selectedPod: String
    var isBusy: Boolean
    var errorText: String?
    var canConnect: Boolean
    var onSelectPod: (String) -> Unit
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
                span {
                    className = ClassName("landing-dialog__eyebrow")
                    +translator.translate("identity.eyebrow", mapOf("identityName" to props.identityName))
                }
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
                className = ClassName("landing-dialog__form")
                div {
                    className = ClassName("form-field")
                    label {
                        className = ClassName("form-label")
                        htmlFor = ElementId("pod-selection")
                        +translator.translate("landing.podLabel")
                    }
                    select {
                        className = ClassName("form-select")
                        id = ElementId("pod-selection")
                        value = props.selectedPod
                        disabled = props.pods.isEmpty() || props.isBusy
                        onChange = { event ->
                            val nextValue = (event.target.asDynamic().value as? String).orEmpty()
                            props.onSelectPod(nextValue)
                        }
                        if (props.pods.isEmpty()) {
                            option {
                                value = ""
                                +translator.translate("landing.noPodConfigured")
                            }
                        } else {
                            option {
                                value = ""
                                disabled = true
                                hidden = props.selectedPod.isNotEmpty()
                                +translator.translate("landing.choosePod")
                            }
                            props.pods.forEach { pod ->
                                option {
                                    value = pod
                                    +pod
                                }
                            }
                        }
                    }
                }
                props.errorText?.let { message ->
                    div {
                        className = ClassName("landing-dialog__error")
                        +message
                    }
                }
            }
            div {
                className = ClassName("landing-dialog__actions")
                button {
                    className = ClassName("button button--primary")
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
                    className = ClassName("button button--secondary")
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
