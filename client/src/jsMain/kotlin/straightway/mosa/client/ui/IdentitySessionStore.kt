package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlin.js.json
import kotlin.runCatching
import kotlin.js.JSON
import org.w3c.dom.Storage

data class IdentitySessionSnapshot(
    val connectedIdentityGid: String?,
    val isProfileVisible: Boolean,
)

interface IdentitySessionStore {
    fun load(): IdentitySessionSnapshot?
    fun save(snapshot: IdentitySessionSnapshot)
}

class BrowserIdentitySessionStore(
    private val storageProvider: () -> Storage? = ::browserStorage,
) : IdentitySessionStore {
    override fun load(): IdentitySessionSnapshot? {
        val raw = runCatching { storageProvider()?.getItem(key) }.getOrNull() ?: return null
        return parse(raw)
    }

    override fun save(snapshot: IdentitySessionSnapshot) {
        runCatching {
            val storage = storageProvider() ?: return
            storage.setItem(key, encode(snapshot))
        }
    }

    private companion object {
        const val key = "identity-session"
        fun browserStorage(): Storage? =
            if (js("typeof window === 'undefined'") as Boolean) {
                null
            } else {
                runCatching { window.localStorage }.getOrNull()
            }

        private fun encode(snapshot: IdentitySessionSnapshot): String =
            JSON.stringify(
                json(
                    "connectedIdentityGid" to snapshot.connectedIdentityGid,
                    "isProfileVisible" to snapshot.isProfileVisible,
                )
            )

        private fun parse(raw: String): IdentitySessionSnapshot? {
            val parsed = runCatching { JSON.parse<dynamic>(raw) }.getOrNull() ?: return null
            val isObject = js("(typeof parsed === 'object') && parsed !== null") as Boolean
            val gid =
                when {
                    parsed is String -> parsed.takeUnless(String::isBlank)
                    isObject -> (parsed.connectedIdentityGid as? String)?.takeUnless { it.isBlank() }
                    else -> null
                }
            val isProfileVisible =
                when {
                    parsed is String -> false
                    isObject -> (parsed.isProfileVisible as? Boolean) ?: false
                    else -> false
                }
            return IdentitySessionSnapshot(gid, isProfileVisible)
        }
    }
}
