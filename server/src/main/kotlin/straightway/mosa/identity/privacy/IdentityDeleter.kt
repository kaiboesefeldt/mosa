package straightway.mosa.identity.privacy

interface IdentityDeleter {
    fun deleteIdentity(gid: String): DeleteIdentityResult
}
