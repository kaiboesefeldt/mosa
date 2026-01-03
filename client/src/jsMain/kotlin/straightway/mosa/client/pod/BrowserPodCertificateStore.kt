package straightway.mosa.client.pod

import kotlinx.serialization.json.Json
import straightway.mosa.auth.PodCertificateResponse
import web.storage.Storage
import web.storage.localStorage

class BrowserPodCertificateStore(
    private val storageProvider: () -> Storage? = ::localStorageOrNull,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
) : PodCertificateStore {
    override fun load(podBase: String, gid: String): PodCertificateResponse? {
        val storage = storageProvider() ?: return null
        val value = storage.getItem(key(podBase, gid)) ?: return null
        return runCatching { json.decodeFromString<PodCertificateResponse>(value) }.getOrNull()
    }

    override fun store(podBase: String, gid: String, certificate: PodCertificateResponse) {
        val storage = storageProvider() ?: return
        storage.setItem(key(podBase, gid), json.encodeToString(certificate))
    }

    override fun remove(podBase: String, gid: String) {
        storageProvider()?.removeItem(key(podBase, gid))
    }

    private fun key(podBase: String, gid: String): String = "mosa:podcert:$podBase:$gid"

    private companion object {
        private fun localStorageOrNull(): Storage? =
            if (js("typeof localStorage === 'undefined'") as Boolean) {
                null
            } else {
                localStorage
            }
    }
}
