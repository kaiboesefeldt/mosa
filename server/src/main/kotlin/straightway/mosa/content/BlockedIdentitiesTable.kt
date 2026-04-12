package straightway.mosa.content

import org.jetbrains.exposed.v1.core.Table

object BlockedIdentitiesTable : Table("blocked_identities") {
    val gid = text("gid")

    override val primaryKey = PrimaryKey(gid)
}
