package straightway.mosa.identity.privacy.snapshot

fun interface FreezeSnapshotNonceGenerator {
    fun generate(): ByteArray
}
