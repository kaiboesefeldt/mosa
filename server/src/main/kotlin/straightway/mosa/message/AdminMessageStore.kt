package straightway.mosa.message

interface AdminMessageStore {
    suspend fun append(record: AdminMessageRecord)

    suspend fun deleteExpired(nowEpochSeconds: Long): Int

    suspend fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
    ): List<AdminMessageRecord>
}
