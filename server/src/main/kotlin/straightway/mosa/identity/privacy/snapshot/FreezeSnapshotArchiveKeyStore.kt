package straightway.mosa.identity.privacy.snapshot

fun interface FreezeSnapshotArchiveKeyStore {
    fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey?
}
