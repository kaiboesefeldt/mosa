package straightway.mosa.content.snapshot

fun interface FreezeSnapshotActiveArchiveKeyProvider {
    fun activeKey(): FreezeSnapshotArchiveKey
}
