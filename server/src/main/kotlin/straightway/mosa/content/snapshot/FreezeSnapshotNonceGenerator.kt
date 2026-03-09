package straightway.mosa.content.snapshot

fun interface FreezeSnapshotNonceGenerator {
    fun generate(): ByteArray
}
