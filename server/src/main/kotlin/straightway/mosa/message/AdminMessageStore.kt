package straightway.mosa.message

interface AdminMessageStore {
    fun append(record: AdminMessageRecord)

    fun restore(record: AdminMessageRecord)

    fun loadForIdentity(gid: String): List<AdminMessageRecord>

    fun deleteByIds(ids: List<String>): Int

    fun deleteForIdentity(gid: String): Int

    fun deleteExpired(nowEpochSeconds: Long): Int

    fun loadVisibleForRecipient(
        recipientGid: String,
        nowEpochSeconds: Long,
    ): List<AdminMessageRecord>

    fun loadVisibleAndShortenExpiry(
        recipientGid: String,
        nowEpochSeconds: Long,
        retrievedExpiryUpperBoundEpochSeconds: Long,
        fromTime: Long = 0,
    ): List<AdminMessageRecord>
}
