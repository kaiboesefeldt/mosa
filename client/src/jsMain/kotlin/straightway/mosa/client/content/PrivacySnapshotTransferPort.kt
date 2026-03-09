package straightway.mosa.client.content

fun interface PrivacySnapshotUploadPort {
    suspend fun upload(): ByteArray?
}

interface PrivacySnapshotTransferPort : PrivacySnapshotUploadPort {
    suspend fun download(snapshot: ByteArray, fileBaseName: String)
}
