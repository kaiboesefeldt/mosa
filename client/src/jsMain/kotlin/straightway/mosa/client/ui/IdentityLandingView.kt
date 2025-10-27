package straightway.mosa.client.ui

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
    div {
        className = ClassName("landing-overlay")
        div {
            className = ClassName("landing-dialog")
            div {
                className = ClassName("landing-dialog__header")
                span {
                    className = ClassName("landing-dialog__eyebrow")
                    +"Identity: ${props.identityName}"
                }
                h1 {
                    className = ClassName("landing-dialog__title")
                    +"Connect to your MOSA pod"
                }
                p {
                    className = ClassName("landing-dialog__description")
                    +"Link your freshly minted identity with a pod to start publishing and querying content."
                }
            }
            div {
                className = ClassName("landing-dialog__form")
                div {
                    className = ClassName("form-field")
                    label {
                        className = ClassName("form-label")
                        htmlFor = ElementId("pod-selection")
                        +"Pod"
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
                                +"No pod configured"
                            }
                        } else {
                            option {
                                value = ""
                                disabled = true
                                hidden = props.selectedPod.isNotEmpty()
                                +"Choose a pod"
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
                    ariaLabel = "Connect"
                    onClick = { props.onConnect() }
                    +(if (props.isBusy) "Connecting..." else "Connect")
                }
                button {
                    className = ClassName("button button--secondary")
                    disabled = props.isBusy
                    ariaLabel = "Generate new identity"
                    onClick = { props.onGenerateNew() }
                    +(if (props.isBusy) "Please wait" else "Generate new identity")
                }
            }
        }
    }
}
