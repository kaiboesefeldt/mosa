package straightway.mosa.content

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class SqliteBlockedIdentityStore(
    private val context: StructuredStateContext,
) : BlockedIdentityStore {
    override fun isBlocked(gid: String): Boolean =
        context.execute {
            BlockedIdentitiesTable
                .selectAll()
                .where { BlockedIdentitiesTable.gid eq gid }
                .limit(1)
                .any()
        }

    override fun block(gid: String): Boolean =
        try {
            context.execute {
                BlockedIdentitiesTable.insert { statement ->
                    statement[BlockedIdentitiesTable.gid] = gid
                }
            }
            true
        } catch (exception: Throwable) {
            if (exception.message?.contains("UNIQUE constraint failed") == true) {
                return false
            }
            throw exception
        }

    override fun unblock(gid: String): Boolean =
        context.execute {
            BlockedIdentitiesTable.deleteWhere {
                BlockedIdentitiesTable.gid eq gid
            } == 1
        }

    override fun delete(gid: String): Boolean = unblock(gid)
}
