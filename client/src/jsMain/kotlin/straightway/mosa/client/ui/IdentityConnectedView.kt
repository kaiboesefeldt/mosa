package straightway.mosa.client.ui

import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var misJwt: String
    var queryPreview: String?
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    div {
        className = ClassName("connected-shell")
        div {
            className = ClassName("connected-shell__header")
            +props.identityName
        }
        div {
            className = ClassName("connected-shell__content")
            div {
                className = ClassName("connected-shell__section")
                span {
                    className = ClassName("connected-shell__section-title")
                    +"Authentication token"
                }
                div {
                    className = ClassName("connected-shell__snippet")
                    pre { +props.misJwt }
                }
            }
            props.queryPreview?.let { preview ->
                div {
                    className = ClassName("connected-shell__section")
                    span {
                        className = ClassName("connected-shell__section-title")
                        +"Prototype query result"
                    }
                    div {
                        className = ClassName("connected-shell__snippet")
                        pre { +preview }
                    }
                }
            }
        }
    }
}
