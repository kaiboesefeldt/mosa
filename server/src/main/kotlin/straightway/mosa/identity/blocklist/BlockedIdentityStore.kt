package straightway.mosa.identity.blocklist

interface BlockedIdentityStore {
    fun isBlocked(gid: String): Boolean

    fun block(gid: String): Boolean

    fun unblock(gid: String): Boolean

    fun delete(gid: String): Boolean
}
