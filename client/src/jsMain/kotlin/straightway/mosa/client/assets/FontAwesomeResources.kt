@file:Suppress("unused", "UNUSED_EXPRESSION")

package straightway.mosa.client.assets

@JsModule("@fortawesome/fontawesome-free/css/all.min.css")
@JsNonModule
private external val fontAwesomeCss: Unit

object FontAwesomeResources {
    fun loadStyles() {
        fontAwesomeCss
    }
}
