package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.OpenPostComplaintsResult

internal val emptyModerationInboxStateSnapshot =
    AdminMessageInboxStateSnapshot(
        knownMessageIds = emptyList(),
        seenMessageIds = emptyList(),
    )

internal fun currentModerationInboxStateKey(
    podBaseProvider: PodBaseProvider,
    connectedIdentityGid: String?,
): Pair<String, String>? {
    val podBase = podBaseProvider.base()?.trim()?.takeUnless(String::isEmpty) ?: return null
    val gid = connectedIdentityGid?.trim()?.takeUnless(String::isEmpty) ?: return null
    return podBase to gid
}

internal fun loadModerationInboxStateSnapshot(
    inboxStateStore: AdminMessageInboxStateStore,
    podBaseProvider: PodBaseProvider,
    connectedIdentityGid: String?,
): AdminMessageInboxStateSnapshot {
    val key = currentModerationInboxStateKey(podBaseProvider, connectedIdentityGid) ?: return emptyModerationInboxStateSnapshot
    return inboxStateStore.load(key.first, key.second) ?: emptyModerationInboxStateSnapshot
}

internal fun persistModerationInboxStateSnapshot(
    inboxStateStore: AdminMessageInboxStateStore,
    podBaseProvider: PodBaseProvider,
    connectedIdentityGid: String?,
    snapshot: AdminMessageInboxStateSnapshot,
) {
    val key = currentModerationInboxStateKey(podBaseProvider, connectedIdentityGid) ?: return
    inboxStateStore.store(key.first, key.second, snapshot)
}

internal fun unreadModerationInboxCount(snapshot: AdminMessageInboxStateSnapshot): Int {
    val seen = snapshot.seenMessageIds.toSet()
    return snapshot.knownMessageIds.count { it !in seen }
}

internal fun mergeComplaintQueueEntries(
    currentEntries: List<ComplaintQueueEntry>,
    result: OpenPostComplaintsResult,
    append: Boolean,
): List<ComplaintQueueEntry> {
    val processedEntries = currentEntries.filterNot(ComplaintQueueEntry::isOpen)
    val openEntries =
        if (append) currentEntries.filter(ComplaintQueueEntry::isOpen) + result.entries.map(OpenPostComplaint::toQueueEntry)
        else result.entries.map(OpenPostComplaint::toQueueEntry)
    return (openEntries + processedEntries).distinctBy(ComplaintQueueEntry::complaintId)
}
