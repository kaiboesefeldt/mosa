package straightway.mosa.content.complaint

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import straightway.mosa.content.StructuredStateDatabase

class SqlitePostComplaintStore(
    private val database: StructuredStateDatabase,
) : PostComplaintStore {
    override suspend fun appendOpen(record: PostComplaintRecord) {
        try {
            database.execute {
                PostComplaintsTable.insert { statement ->
                    statement[id] = record.id
                    statement[postId] = record.postId
                    statement[complainantGid] = record.complainantGid
                    statement[postAuthorGid] = record.postAuthorGid
                    statement[reason] = record.reason
                    statement[createdAt] = record.createdAt
                    statement[complainantSignature] = record.complainantSignature
                    statement[complainantSignatureVersion] = record.complainantSignatureVersion
                    statement[state] = record.state
                    statement[decidedByAdminGid] = record.decidedByAdminGid
                    statement[decidedAt] = record.decidedAt
                    statement[feedback] = record.feedback
                    statement[adminSignature] = record.adminSignature
                    statement[adminSignatureVersion] = record.adminSignatureVersion
                    statement[openMarker] = OPEN_MARKER
                }
            }
        } catch (exception: Throwable) {
            if (exception.message?.contains("UNIQUE constraint failed") == true) {
                throw PostComplaintConflictException("an open complaint already exists for this post")
            }
            throw exception
        }
    }

    override suspend fun loadLatestForPost(postId: String): PostComplaintRecord? =
        database.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.postId eq postId }
                .orderBy(PostComplaintsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map(::toRecord)
                .singleOrNull()
        }

    override suspend fun deleteForIdentity(gid: String): Int =
        database.execute {
            PostComplaintsTable.deleteWhere {
                (complainantGid eq gid) or
                    (postAuthorGid eq gid)
            }
        }

    override suspend fun loadById(id: String): PostComplaintRecord? =
        database.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.id eq id }
                .limit(1)
                .map(::toRecord)
                .singleOrNull()
        }

    override suspend fun countOpen(): Int =
        database.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.openMarker eq OPEN_MARKER }
                .count()
                .toInt()
        }

    override suspend fun loadPage(
        limit: Int,
        offset: Int,
        includeProcessed: Boolean,
    ): List<PostComplaintRecord> =
        database.execute {
            val query = PostComplaintsTable.selectAll()
            val filteredQuery =
                if (includeProcessed) {
                    query
                } else {
                    query.where { PostComplaintsTable.openMarker eq OPEN_MARKER }
                }
            filteredQuery
                .orderBy(PostComplaintsTable.createdAt, SortOrder.ASC)
                .limit(limit)
                .offset(offset.toLong())
                .map(::toRecord)
        }

    override suspend fun decide(
        complaintId: String,
        expectedState: PostComplaintState,
        state: PostComplaintState,
        adminGid: String,
        decidedAt: Long,
        feedback: String,
        adminSignature: String,
        adminSignatureVersion: Int,
    ): Boolean =
        database.execute {
            PostComplaintsTable.update(
                where = {
                    (PostComplaintsTable.id eq complaintId) and
                        (PostComplaintsTable.state eq expectedState)
                },
            ) { statement ->
                statement[PostComplaintsTable.state] = state
                statement[decidedByAdminGid] = adminGid
                statement[PostComplaintsTable.decidedAt] = decidedAt
                statement[PostComplaintsTable.feedback] = feedback
                statement[PostComplaintsTable.adminSignature] = adminSignature
                statement[PostComplaintsTable.adminSignatureVersion] = adminSignatureVersion
                statement[openMarker] = null
            } == 1
        }

    private fun toRecord(row: ResultRow): PostComplaintRecord =
        PostComplaintRecord(
            id = row[PostComplaintsTable.id],
            postId = row[PostComplaintsTable.postId],
            complainantGid = row[PostComplaintsTable.complainantGid],
            postAuthorGid = row[PostComplaintsTable.postAuthorGid],
            reason = row[PostComplaintsTable.reason],
            createdAt = row[PostComplaintsTable.createdAt],
            complainantSignature = row[PostComplaintsTable.complainantSignature],
            complainantSignatureVersion = row[PostComplaintsTable.complainantSignatureVersion],
            state = row[PostComplaintsTable.state],
            decidedByAdminGid = row[PostComplaintsTable.decidedByAdminGid],
            decidedAt = row[PostComplaintsTable.decidedAt],
            feedback = row[PostComplaintsTable.feedback],
            adminSignature = row[PostComplaintsTable.adminSignature],
            adminSignatureVersion = row[PostComplaintsTable.adminSignatureVersion],
        )

    private companion object {
        const val OPEN_MARKER = "OPEN"
    }
}
