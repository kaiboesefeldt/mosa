package straightway.mosa.client.ui

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import straightway.mosa.client.i18n.Translator

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var misJwt: String
    var queryPreview: String?
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
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
                    +translator.translate("connected.authenticationTokenTitle")
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
                        +translator.translate("connected.prototypeQueryResultTitle")
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

private object ConnectedViewInjector : KoinComponent
