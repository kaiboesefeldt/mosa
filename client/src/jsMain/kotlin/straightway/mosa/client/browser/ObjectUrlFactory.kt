package straightway.mosa.client.browser

import kotlinx.browser.window
import kotlin.js.unsafeCast
import org.w3c.files.Blob

interface ObjectUrlFactory {
    fun create(blob: Blob): ObjectUrlHandle
}

class ObjectUrlHandle(
    val value: String,
    private val revokeAction: (String) -> Unit,
) {
    fun revoke() {
        revokeAction(value)
    }
}

class WindowObjectUrlFactory(
    private val provider: UrlApiProvider = WindowUrlApiProvider,
) : ObjectUrlFactory {
    override fun create(blob: Blob): ObjectUrlHandle {
        val api = provider.provide() ?: error("URL API not available")
        val url = api.createObjectURL(blob)
        return ObjectUrlHandle(url) { api.revokeObjectURL(it) }
    }
}

fun interface UrlApiProvider {
    fun provide(): UrlApi?
}

external interface UrlApi {
    fun createObjectURL(blob: Blob): String
    fun revokeObjectURL(url: String)
}

object WindowUrlApiProvider : UrlApiProvider {
    @Suppress("UnsafeCastFromDynamic")
    override fun provide(): UrlApi? {
        val candidate = window.asDynamic().URL ?: return null
        return candidate.unsafeCast<UrlApi>()
    }
}
