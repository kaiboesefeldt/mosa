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
    fun `listForRecipient returns only visible messages sorted newest first`() =
        runTest {
            coEvery {
                store.loadVisibleAndShortenExpiry("recipient-gid", 1_773_742_530, 1_776_161_730)
            } returns
                listOf(
                    AdminMessageRecord("older", "recipient-gid", "admin-a", 20, "Older", "Body 1", 100),
                    AdminMessageRecord("newer", "recipient-gid", "admin-b", 30, "Newer", "Body 2", 100),
                )

            val result = sut.listForRecipient("recipient-gid")

            assertEquals(
                AdminMessageListResult(
                    listOf(
                        AdminMessage("newer", "admin-b", 30, "Newer", "Body 2"),
                        AdminMessage("older", "admin-a", 20, "Older", "Body 1"),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `deleteExpired delegates to store with current time`() =
        runTest {
            coEvery { store.deleteExpired(1_773_742_530) } returns 3

            val deleted = sut.deleteExpired()

            assertEquals(3, deleted)
        }
}
