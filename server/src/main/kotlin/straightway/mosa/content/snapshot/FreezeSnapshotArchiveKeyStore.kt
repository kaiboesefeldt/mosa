package straightway.mosa.content.snapshot

fun interface FreezeSnapshotArchiveKeyStore {
    fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey?
}
