package straightway.mosa.message

import io.ktor.server.plugins.BadRequestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days

class AdminMessageServiceTest {
    private val store = mockk<AdminMessageStore>(relaxed = true)
    private val clock = Clock.fixed(Instant.parse("2026-03-17T10:15:30Z"), ZoneOffset.UTC)
    private val sut =
        AdminMessageService(
            store = store,
            clock = clock,
            isAdmin = { it == "admin-gid" },
            unretrievedTtl = 180.days,
            retrievedTtl = 28.days,
            pageSize = 10,
            idFactory = { "message-1" },
        )

    @Test
    fun `send stores immutable message with long ttl for admins`() =
        runTest {
            sut.send(
                senderGid = "admin-gid",
                request =
                    CreateAdminMessageRequest(
                        recipientGid = "recipient-gid",
                        subject = "Subject",
                        body = "Body",
                    ),
            )

            coVerify(exactly = 1) {
                store.append(
                    AdminMessageRecord(
                        id = "message-1",
                        recipientGid = "recipient-gid",
                        senderGid = "admin-gid",
                        createdAt = 1_773_742_530,
                        subject = "Subject",
                        body = "Body",
                        expiresAt = 1_789_294_530,
                    ),
                )
            }
        }

    @Test
    fun `send rejects non admins`() =
        runTest {
            assertFailsWith<AdminMessageService.AdminMessageForbiddenException> {
                sut.send(
                    senderGid = "user-gid",
                    request = CreateAdminMessageRequest("recipient-gid", "Subject", "Body"),
                )
            }

            coVerify(exactly = 0) { store.append(any()) }
        }

    @Test
    fun `send rejects blank fields`() =
        runTest {
            assertFailsWith<BadRequestException> {
                sut.send(
                    senderGid = "admin-gid",
                    request = CreateAdminMessageRequest("recipient-gid", " ", "Body"),
                )
            }
        }

    @Test
    fun `listInboxForRecipient returns first page sorted newest first with metadata`() =
        runTest {
            coEvery {
                store.loadVisibleAndShortenExpiry("recipient-gid", 1_773_742_530, 1_776_161_730)
            } returns
                listOf(
                    AdminMessageRecord("older", "recipient-gid", "admin-a", 20, "Older", "Body 1", 1_773_828_929),
                    AdminMessageRecord("newer", "recipient-gid", "admin-b", 30, "Newer", "Body 2", 1_773_742_531),
                )

            val result = sut.listInboxForRecipient("recipient-gid", page = 1)

            assertEquals(
                AdminMessageListResult(
                    listOf(
                        AdminMessage("newer", "admin-b", 30, "Newer", "Body 2", 1),
                        AdminMessage("older", "admin-a", 20, "Older", "Body 1", 1),
                    ),
                    page = 1,
                    hasMore = false,
                ),
                result,
            )
        }

    @Test
    fun `listInboxForRecipient returns requested page and hasMore flag`() =
        runTest {
            coEvery {
                store.loadVisibleAndShortenExpiry("recipient-gid", 1_773_742_530, 1_776_161_730)
            } returns
                (1..11).map { index ->
                    AdminMessageRecord(
                        id = "message-$index",
                        recipientGid = "recipient-gid",
                        senderGid = "admin-$index",
                        createdAt = index.toLong(),
                        subject = "Subject $index",
                        body = "Body $index",
                        expiresAt = 1_773_742_530 + 86_400,
                    )
                }

            val result = sut.listInboxForRecipient("recipient-gid", page = 2)

            assertEquals(2, result.page)
            assertEquals(false, result.hasMore)
            assertEquals(listOf("message-1"), result.entries.map(AdminMessage::id))
        }

    @Test
    fun `listAdminMessagesForRecipient rejects non admins`() =
        runTest {
            assertFailsWith<AdminMessageService.AdminMessageForbiddenException> {
                sut.listAdminMessagesForRecipient(
                    requesterGid = "user-gid",
                    recipientGid = "recipient-gid",
                    page = 1,
                )
            }
        }

    @Test
    fun `listAdminMessagesForRecipient keeps ttl unchanged and returns all admins for page`() =
        runTest {
            coEvery {
                store.loadVisibleForRecipient("recipient-gid", 1_773_742_530)
            } returns
                (1..11).map { index ->
                    AdminMessageRecord(
                        id = "message-$index",
                        recipientGid = "recipient-gid",
                        senderGid = "admin-$index",
                        createdAt = index.toLong(),
                        subject = "Subject $index",
                        body = "Body $index",
                        expiresAt = 1_773_742_530 + (index * 86_400L),
                    )
                }

            val result =
                sut.listAdminMessagesForRecipient(
                    requesterGid = "admin-gid",
                    recipientGid = "recipient-gid",
                    page = 1,
                )

            assertEquals(1, result.page)
            assertEquals(true, result.hasMore)
            assertEquals((11 downTo 2).map { "message-$it" }, result.entries.map(AdminMessage::id))
            assertEquals(11, result.entries.first().remainingLifetimeDays)
            coVerify(exactly = 1) { store.loadVisibleForRecipient("recipient-gid", 1_773_742_530) }
            coVerify(exactly = 0) { store.loadVisibleAndShortenExpiry(any(), any(), any()) }
        }

    @Test
    fun `deleteExpired delegates to store with current time`() =
        runTest {
            coEvery { store.deleteExpired(1_773_742_530) } returns 3

            val deleted = sut.deleteExpired()

            assertEquals(3, deleted)
        }
}
