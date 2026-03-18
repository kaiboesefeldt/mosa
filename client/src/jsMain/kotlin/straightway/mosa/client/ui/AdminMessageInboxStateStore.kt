package straightway.mosa.client.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import web.storage.Storage
import web.storage.localStorage

@Serializable
data class AdminMessageInboxStateSnapshot(
    val knownMessageIds: List<String>,
    val seenMessageIds: List<String>,
)

interface AdminMessageInboxStateStore {
    fun load(
        podBase: String,
        gid: String,
    ): AdminMessageInboxStateSnapshot?

    fun store(
        podBase: String,
        gid: String,
        snapshot: AdminMessageInboxStateSnapshot,
    )
}

class BrowserAdminMessageInboxStateStore(
    private val storageProvider: () -> Storage? = ::localStorageOrNull,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
) : AdminMessageInboxStateStore {
    override fun load(
        podBase: String,
        gid: String,
    ): AdminMessageInboxStateSnapshot? {
        val storage = storageProvider() ?: return null
        val value = storage.getItem(key(podBase, gid)) ?: return null
        val snapshot = runCatching { json.decodeFromString<AdminMessageInboxStateSnapshot>(value) }.getOrNull() ?: return null
        return snapshot.normalized()
    }

    override fun store(
        podBase: String,
        gid: String,
        snapshot: AdminMessageInboxStateSnapshot,
    ) {
        val storage = storageProvider() ?: return
        storage.setItem(key(podBase, gid), json.encodeToString(snapshot.normalized()))
    }

    private fun key(
        podBase: String,
        gid: String,
    ): String = "mosa:admin-message-inbox:$podBase:$gid"

    private companion object {
        private fun localStorageOrNull(): Storage? =
            if (js("typeof localStorage === 'undefined'") as Boolean) {
                null
            } else {
                localStorage
            }
    }
}

private fun AdminMessageInboxStateSnapshot.normalized(): AdminMessageInboxStateSnapshot {
    val known = knownMessageIds.map(String::trim).filter(String::isNotEmpty).distinct()
    val seen = seenMessageIds.map(String::trim).filter(String::isNotEmpty).distinct().filter(known::contains)
    return AdminMessageInboxStateSnapshot(knownMessageIds = known, seenMessageIds = seen)
}
