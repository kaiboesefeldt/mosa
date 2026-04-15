package straightway.mosa.client.identity.privacy

fun interface PrivacySnapshotUploadPort {
    suspend fun upload(): ByteArray?
}

interface PrivacySnapshotTransferPort : PrivacySnapshotUploadPort {
    suspend fun download(snapshot: ByteArray, fileBaseName: String)
}
