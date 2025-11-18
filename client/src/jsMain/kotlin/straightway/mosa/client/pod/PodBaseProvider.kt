package straightway.mosa.client.pod

class PodBaseProvider(
    private val windowProvider: () -> dynamic = ::currentWindow,
) {
    fun base(): String? {
        val windowBase = runCatching { windowProvider()?.asDynamic()?.MOSA_POD_BASE as? String }.getOrNull()
        val globalBase = runCatching { js("globalThis.MOSA_POD_BASE") as? String }.getOrNull()
        return (windowBase ?: globalBase)?.takeUnless(String::isBlank)
    }

    private companion object {
        @Suppress("UnsafeCastFromDynamic")
        private fun currentWindow(): dynamic =
            if (js("typeof window === 'undefined'") as Boolean) {
                null
            } else {
                kotlinx.browser.window
            }
    }
}
