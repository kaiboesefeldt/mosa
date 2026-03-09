package straightway.mosa.content.snapshot

data class FreezeSnapshotArchiveKey(
    val keyId: String,
    val secret: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FreezeSnapshotArchiveKey

        if (keyId != other.keyId) return false
        if (!secret.contentEquals(other.secret)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyId.hashCode()
        result = 31 * result + secret.contentHashCode()
        return result
    }
}
