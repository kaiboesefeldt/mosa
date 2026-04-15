package straightway.mosa.identity.privacy.snapshot

fun interface FreezeSnapshotActiveArchiveKeyProvider {
    fun activeKey(): FreezeSnapshotArchiveKey
}
