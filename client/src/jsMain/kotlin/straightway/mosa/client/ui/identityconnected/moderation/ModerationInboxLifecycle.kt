package straightway.mosa.client.ui.identityconnected.moderation

import react.RefObject
import react.useEffect
import straightway.mosa.client.ui.AdminMessageInboxStateSnapshot

internal fun useModerationInboxLifecycle(
    connectedIdentityGid: String?,
    currentConnectedIdentityGid: String?,
    setConnectedIdentityGid: (String?) -> Unit,
    loadInboxStateSnapshot: () -> AdminMessageInboxStateSnapshot,
    refreshInbox: (Boolean) -> Unit,
    inboxEntries: List<straightway.mosa.message.AdminMessage>,
    isInboxAppending: Boolean,
    inboxPopoverRef: RefObject<web.html.HTMLDivElement>,
    inboxScrollRestoreRef: RefObject<Double>,
    setInboxEntries: (List<straightway.mosa.message.AdminMessage>) -> Unit,
    setSeenInboxMessageIds: (List<String>) -> Unit,
    setHiddenSeenInboxMessageIds: (List<String>) -> Unit,
    setShowSeenInboxEntries: (Boolean) -> Unit,
    setUnreadInboxCount: (Int) -> Unit,
    setInboxError: (String?) -> Unit,
    setInboxLoading: (Boolean) -> Unit,
    setInboxOpen: (Boolean) -> Unit,
) {
    useEffect(connectedIdentityGid) {
        val storedSnapshot = loadInboxStateSnapshot()
        setInboxEntries(storedSnapshot.entries)
        setSeenInboxMessageIds(storedSnapshot.seenMessageIds)
        setHiddenSeenInboxMessageIds(storedSnapshot.seenMessageIds)
        setShowSeenInboxEntries(false)
        setUnreadInboxCount(unreadModerationInboxCount(storedSnapshot))
        setInboxError(null)
        val nextConnectedIdentityGid = connectedIdentityGid
        if (nextConnectedIdentityGid == currentConnectedIdentityGid) return@useEffect
        setConnectedIdentityGid(nextConnectedIdentityGid)
        if (nextConnectedIdentityGid == null) {
            setInboxLoading(false)
            setInboxOpen(false)
            return@useEffect
        }
        refreshInbox(false)
    }

    useEffect(arrayOf(inboxEntries, isInboxAppending)) {
        val scrollTop = inboxScrollRestoreRef.current ?: return@useEffect
        inboxPopoverRef.current?.scrollTop = scrollTop
        if (!isInboxAppending) {
            inboxScrollRestoreRef.current = null
        }
    }
}
