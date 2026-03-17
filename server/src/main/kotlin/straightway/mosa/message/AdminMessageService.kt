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

    suspend fun listForRecipient(recipientGid: String): AdminMessageListResult {
        val now = clock.instant().epochSecond
        val records =
            store.loadVisibleAndShortenExpiry(
                recipientGid = recipientGid,
                nowEpochSeconds = now,
                retrievedExpiryUpperBoundEpochSeconds = now + retrievedTtl.inWholeSeconds,
            )
        return AdminMessageListResult(
            records
                .sortedByDescending { it.createdAt }
                .map { record ->
                    AdminMessage(
                        id = record.id,
                        senderGid = record.senderGid,
                        createdAt = record.createdAt,
                        subject = record.subject,
                        body = record.body,
                    )
                },
        )
    }

    suspend fun deleteExpired(): Int = store.deleteExpired(clock.instant().epochSecond)

    private fun requireText(
        value: String,
        fieldName: String,
    ) {
        if (value.isBlank()) {
            throw BadRequestException("$fieldName must not be blank")
        }
    }
}
