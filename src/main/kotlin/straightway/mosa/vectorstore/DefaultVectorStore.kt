package straightway.mosa.vectorstore

import straightway.mosa.VectorStore

class DefaultVectorStore : VectorStore {
    override suspend fun add(
        id: String,
        vector: FloatArray,
    ) = TODO("Not yet implemented")

    override suspend fun search(
        vector: FloatArray,
        minSimilarity: Float,
    ) = TODO("Not yet implemented")
}
