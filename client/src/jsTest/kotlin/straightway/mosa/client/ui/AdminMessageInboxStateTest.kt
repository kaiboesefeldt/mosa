package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.message.AdminMessage

class AdminMessageInboxStateTest {
    @Test
    fun `next refresh uses latest locally known message time`() {
        val snapshot =
            AdminMessageInboxStateSnapshot(
                entries =
                    listOf(
                        AdminMessage("message-1", "sender", 10, "Subject 1", "Body 1"),
                        AdminMessage("message-2", "sender", 20, "Subject 2", "Body 2"),
                    ),
                seenMessageIds = listOf("message-1"),
            )

        assertEquals(20, snapshot.nextRefreshFromTime())
    }

    @Test
    fun `merge retains local messages and deduplicates fetched boundary entries`() {
        val snapshot =
            AdminMessageInboxStateSnapshot(
                entries =
                    listOf(
                        AdminMessage("message-1", "sender", 10, "Local old", "Body 1"),
                        AdminMessage("message-2", "sender", 20, "Local boundary", "Body 2", remainingLifetimeDays = 3),
                    ),
                seenMessageIds = listOf("message-1"),
            )

        val merged =
            snapshot.mergeFetchedEntries(
                fetchedEntries =
                    listOf(
                        AdminMessage("message-2", "sender", 20, "Fetched boundary", "Body 2", remainingLifetimeDays = 1),
                        AdminMessage("message-3", "sender", 30, "Fetched new", "Body 3"),
                    ),
                markFetchedEntriesAsSeen = false,
            )

        assertEquals(listOf("message-3", "message-2", "message-1"), merged.entries.map(AdminMessage::id))
        assertEquals("Fetched boundary", merged.entries[1].subject)
        assertEquals(listOf("message-1"), merged.seenMessageIds)
        assertEquals(listOf("message-3", "message-2", "message-1"), merged.knownMessageIds)
    }

    @Test
    fun `hidden read filter keeps only messages that were unread when inbox opened`() {
        val entries =
            listOf(
                AdminMessage("message-3", "sender", 30, "Newest", "Body 3"),
                AdminMessage("message-2", "sender", 20, "Boundary", "Body 2"),
                AdminMessage("message-1", "sender", 10, "Old read", "Body 1"),
            )

        val visible = entries.filteredForInbox(showSeenMessages = false, hiddenSeenMessageIds = listOf("message-2", "message-1"))

        assertEquals(listOf("message-3"), visible.map(AdminMessage::id))
    }
}
