package straightway.mosa.message

import io.ktor.server.plugins.BadRequestException
import java.time.Clock
import kotlin.time.Duration

class AdminMessageService(
    private val store: AdminMessageStore,
    private val clock: Clock,
    private val isAdmin: suspend (String) -> Boolean,
    private val unretrievedTtl: Duration,
    private val retrievedTtl: Duration,
    private val pageSize: Int,
    private val idFactory: () -> String,
) {
    class AdminMessageForbiddenException : RuntimeException("not allowed to send admin messages")

    suspend fun send(
        senderGid: String,
        request: CreateAdminMessageRequest,
    ) {
        if (!isAdmin(senderGid)) {
            throw AdminMessageForbiddenException()
        }
        requireText(request.recipientGid, "recipientGid")
        requireText(request.subject, "subject")
        requireText(request.body, "body")

        val createdAt = clock.instant().epochSecond
        store.append(
            AdminMessageRecord(
                id = idFactory(),
                recipientGid = request.recipientGid.trim(),
                senderGid = senderGid,
                createdAt = createdAt,
                subject = request.subject,
                body = request.body,
                expiresAt = createdAt + unretrievedTtl.inWholeSeconds,
            ),
        )
    }

    suspend fun listInboxForRecipient(
        recipientGid: String,
        page: Int,
    ): AdminMessageListResult {
        val now = clock.instant().epochSecond
        return pagedResult(
            store.loadVisibleAndShortenExpiry(
                recipientGid = recipientGid,
                nowEpochSeconds = now,
                retrievedExpiryUpperBoundEpochSeconds = now + retrievedTtl.inWholeSeconds,
            ),
            now = now,
            page = page,
        )
    }

    suspend fun listAdminMessagesForRecipient(
        requesterGid: String,
        recipientGid: String,
        page: Int,
    ): AdminMessageListResult {
        if (!isAdmin(requesterGid)) {
            throw AdminMessageForbiddenException()
        }
        val now = clock.instant().epochSecond
        return pagedResult(
            store.loadVisibleForRecipient(
                recipientGid = recipientGid,
                nowEpochSeconds = now,
            ),
            now = now,
            page = page,
        )
    }

    suspend fun deleteExpired(): Int = store.deleteExpired(clock.instant().epochSecond)

    private fun pagedResult(
        records: List<AdminMessageRecord>,
        now: Long,
        page: Int,
    ): AdminMessageListResult {
        requirePage(page)
        val sorted = records.sortedByDescending { it.createdAt }
        val offset = (page - 1) * pageSize
        val pageRecords = sorted.drop(offset).take(pageSize)
        return AdminMessageListResult(
            entries =
                pageRecords.map { record ->
                    AdminMessage(
                        id = record.id,
                        senderGid = record.senderGid,
                        createdAt = record.createdAt,
                        subject = record.subject,
                        body = record.body,
                        remainingLifetimeDays = remainingLifetimeDays(record.expiresAt, now),
                    )
                },
            page = page,
            hasMore = offset + pageRecords.size < sorted.size,
        )
    }

    private fun remainingLifetimeDays(
        expiresAt: Long,
        now: Long,
    ): Int = ((expiresAt - now + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt()

    private fun requirePage(page: Int) {
        if (page < 1) {
            throw BadRequestException("page must be at least 1")
        }
    }

    private fun requireText(
        value: String,
        fieldName: String,
    ) {
        if (value.isBlank()) {
            throw BadRequestException("$fieldName must not be blank")
        }
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
