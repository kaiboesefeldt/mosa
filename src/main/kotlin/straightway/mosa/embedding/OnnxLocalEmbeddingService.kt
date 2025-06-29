package straightway.mosa.embedding

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory
import ai.djl.repository.zoo.Criteria.builder
import ai.djl.repository.zoo.ModelZoo
import kotlinx.coroutines.runBlocking
import straightway.mosa.EmbeddingService

class OnnxLocalEmbeddingService(
    modelUrl: String = "djl://ai.djl.huggingface.onnxruntime/TaylorAI/bge-micro-v2",
) : EmbeddingService,
    AutoCloseable {
    private val model =
        ModelZoo.loadModel(
            builder()
                .setTypes(String::class.java, FloatArray::class.java)
                .optModelUrls(modelUrl)
                .optTranslatorFactory(TextEmbeddingTranslatorFactory())
                .build(),
        )
    private val predictor = model.newPredictor()

    override val dimension: Int by lazy { runBlocking { embed("determine embedding size").size } }

    override suspend fun embed(text: String): FloatArray = predictor.predict(text)

    override fun close() {
        predictor.close()
        model.close()
    }
}
