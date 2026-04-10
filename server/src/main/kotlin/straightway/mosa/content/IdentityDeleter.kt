package straightway.mosa.content

interface IdentityDeleter {
    fun deleteIdentity(gid: String): DeleteIdentityResult
}
