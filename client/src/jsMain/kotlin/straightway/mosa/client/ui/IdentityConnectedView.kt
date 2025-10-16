package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.js.unsafeCast
import react.CSSProperties
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.small

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var misJwt: String
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    div {
        style = json("textAlign" to "right").unsafeCast<CSSProperties>()
        small { +props.identityName }
    }
    div {
        style = json("textAlign" to "center").unsafeCast<CSSProperties>()
        pre { +props.misJwt }
    }
}
