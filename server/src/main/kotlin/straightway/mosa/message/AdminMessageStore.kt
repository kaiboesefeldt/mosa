package straightway.mosa.message

interface AdminMessageStore {
    suspend fun append(record: AdminMessageRecord)

    suspend fun deleteForIdentity(gid: String): Int

    suspend fun deleteExpired(nowEpochSeconds: Long): Int

    suspend fun loadVisibleForRecipient(
        recipientGid: String,
        nowEpochSeconds: Long,
    ): List<AdminMessageRecord>

    suspend fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
        fromTime: Long = 0,
    ): List<AdminMessageRecord>
}
