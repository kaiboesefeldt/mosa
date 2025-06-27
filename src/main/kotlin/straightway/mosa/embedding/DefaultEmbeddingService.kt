package straightway.mosa.embedding

import straightway.mosa.EmbeddingService

class DefaultEmbeddingService : EmbeddingService {
    override val dimension: Int get() = TODO("Not yet implemented")

    override suspend fun embed(text: String): FloatArray = TODO("Not yet implemented")
}
