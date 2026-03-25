package straightway.mosa.client.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import web.storage.Storage
import web.storage.localStorage
import straightway.mosa.message.AdminMessage

@Serializable
data class AdminMessageInboxStateSnapshot(
    val knownMessageIds: List<String> = emptyList(),
    val seenMessageIds: List<String> = emptyList(),
    val entries: List<AdminMessage> = emptyList(),
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
    val entries = entries.distinctBy(AdminMessage::id).sortedByDescending(AdminMessage::createdAt)
    val known = (entries.map(AdminMessage::id) + knownMessageIds).map(String::trim).filter(String::isNotEmpty).distinct()
    val seen = seenMessageIds.map(String::trim).filter(String::isNotEmpty).distinct().filter(known::contains)
    return AdminMessageInboxStateSnapshot(knownMessageIds = known, seenMessageIds = seen, entries = entries)
}

internal fun AdminMessageInboxStateSnapshot.nextRefreshFromTime(): Long = entries.maxOfOrNull(AdminMessage::createdAt) ?: 0

internal fun AdminMessageInboxStateSnapshot.mergeFetchedEntries(
    fetchedEntries: List<AdminMessage>,
    markFetchedEntriesAsSeen: Boolean,
): AdminMessageInboxStateSnapshot {
    val normalized = normalized()
    val mergedEntries = (fetchedEntries + normalized.entries).distinctBy(AdminMessage::id).sortedByDescending(AdminMessage::createdAt)
    val knownMessageIds = mergedEntries.map(AdminMessage::id)
    val seenMessageIds =
        (
            normalized.seenMessageIds +
                if (markFetchedEntriesAsSeen) {
                    fetchedEntries.map(AdminMessage::id)
                } else {
                    emptyList()
                }
        ).distinct().filter(knownMessageIds::contains)
    return AdminMessageInboxStateSnapshot(
        knownMessageIds = knownMessageIds,
        seenMessageIds = seenMessageIds,
        entries = mergedEntries,
    )
}

internal fun List<AdminMessage>.filteredForInbox(
    showSeenMessages: Boolean,
    hiddenSeenMessageIds: List<String>,
): List<AdminMessage> {
    if (showSeenMessages) {
        return this
    }
    val hiddenSeen = hiddenSeenMessageIds.toSet()
    return filterNot { it.id in hiddenSeen }
}
