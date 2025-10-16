package straightway.mosa.client.ui

import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.select

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
    h1 { +props.identityName }
    props.errorText?.let { message ->
        p { +message }
    }
    select {
        value = props.selectedPod
        disabled = props.pods.isEmpty()
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
            props.pods.forEach { pod ->
                option {
                    value = pod
                    +pod
                }
            }
        }
    }
    button {
        disabled = props.isBusy || !props.canConnect
        onClick = { props.onConnect() }
        +"Connect"
    }
    button {
        disabled = props.isBusy
        onClick = { props.onGenerateNew() }
        +"Generate new identity"
    }
}
