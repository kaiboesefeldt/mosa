@file:JsModule("@testing-library/react")
@file:JsNonModule

package straightway.mosa.client.testing

import org.w3c.dom.Element
import react.ReactNode

external fun render(
    ui: ReactNode,
    options: dynamic = definedExternally,
): RenderResult

external fun cleanup()

external val screen: Screen

@JsName("fireEvent")
external val fireEvent: FireEvent

external interface Screen {
    fun getByRole(role: dynamic, options: dynamic = definedExternally): Element
}

external interface FireEvent {
    fun change(element: Element, event: dynamic = definedExternally): Boolean
    fun click(element: Element, event: dynamic = definedExternally): Boolean
}

external interface RenderResult {
    val container: Element
}
