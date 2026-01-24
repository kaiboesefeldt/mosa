@file:Suppress("unused", "UNUSED_EXPRESSION")

package straightway.mosa.client.assets

@JsModule("@assets/logo.png")
@JsNonModule
private external val logoPng: String

object LogoResources {
    val dataUrl: String = logoPng
}
