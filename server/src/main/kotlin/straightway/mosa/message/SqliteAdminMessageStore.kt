package straightway.mosa.message

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import straightway.mosa.content.StructuredStateDatabase

class SqliteAdminMessageStore(
    private val database: StructuredStateDatabase,
) : AdminMessageStore {
    override suspend fun append(record: AdminMessageRecord) {
        database.execute {
            AdminMessagesTable.insert { statement ->
                statement[id] = record.id
                statement[recipientGid] = record.recipientGid
                statement[senderGid] = record.senderGid
                statement[createdAt] = record.createdAt
                statement[subject] = record.subject
                statement[body] = record.body
                statement[expiresAt] = record.expiresAt
            }
        }
    }

    override suspend fun deleteExpired(nowEpochSeconds: Long): Int =
        database.execute {
            AdminMessagesTable.deleteWhere {
                expiresAt lessEq nowEpochSeconds
            }
        }

    override suspend fun deleteForIdentity(gid: String): Int =
        database.execute {
            AdminMessagesTable.deleteWhere {
                (recipientGid eq gid) or
                    (senderGid eq gid)
            }
        }

    override suspend fun loadVisibleForRecipient(
        recipientGid: String,
        nowEpochSeconds: Long,
    ): List<AdminMessageRecord> =
        database.execute {
            AdminMessagesTable.deleteWhere {
                expiresAt lessEq nowEpochSeconds
            }
            AdminMessagesTable
                .selectAll()
                .where {
                    (AdminMessagesTable.recipientGid eq recipientGid) and
                        (AdminMessagesTable.expiresAt greater nowEpochSeconds)
                }.map(::toRecord)
        }

    override suspend fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
        fromTime: Long,
    ): List<AdminMessageRecord> =
        database.execute {
            AdminMessagesTable.deleteWhere {
                expiresAt lessEq nowEpochSeconds
            }
            val visible =
                AdminMessagesTable
                    .selectAll()
                    .where {
                        (AdminMessagesTable.recipientGid eq recipientGid) and
                            (AdminMessagesTable.expiresAt greater nowEpochSeconds) and
                            (AdminMessagesTable.createdAt greaterEq fromTime)
                    }.map(::toRecord)

            visible.forEach { record ->
                AdminMessagesTable.update(where = { AdminMessagesTable.id eq record.id }) { statement ->
                    statement[expiresAt] = minOf(record.expiresAt, retrievedExpiryUpperBoundEpochSeconds)
                }
            }

            visible.map { record ->
                record.copy(expiresAt = minOf(record.expiresAt, retrievedExpiryUpperBoundEpochSeconds))
            }
        }

    private fun toRecord(row: ResultRow): AdminMessageRecord =
        AdminMessageRecord(
            id = row[AdminMessagesTable.id],
            recipientGid = row[AdminMessagesTable.recipientGid],
            senderGid = row[AdminMessagesTable.senderGid],
            createdAt = row[AdminMessagesTable.createdAt],
            subject = row[AdminMessagesTable.subject],
            body = row[AdminMessagesTable.body],
            expiresAt = row[AdminMessagesTable.expiresAt],
        )
}
