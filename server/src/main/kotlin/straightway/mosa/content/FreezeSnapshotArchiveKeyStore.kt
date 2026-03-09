package straightway.mosa.content

fun interface FreezeSnapshotArchiveKeyStore {
    fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey?
}
