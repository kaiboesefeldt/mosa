package straightway.mosa

interface EmbeddingService {
    val dimension: Int

    suspend fun embed(text: String): FloatArray
}
