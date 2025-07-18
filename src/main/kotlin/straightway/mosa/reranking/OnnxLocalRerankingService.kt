package straightway.mosa.reranking

import ai.djl.inference.Predictor
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import straightway.mosa.RerankingService
import java.nio.file.Path

class OnnxLocalRerankingService(
    modelPath: Path,
    val chunkSize: Int = 1,
) : RerankingService,
    AutoCloseable {
    private val model: ZooModel<String, Float>
    private val predictor: Predictor<String, Float>

    init {
        val criteria =
            Criteria
                .builder()
                .setTypes(String::class.java, Float::class.java)
                .optModelPath(
                    modelPath.resolve("onnx"),
                ).optEngine("OnnxRuntime")
                .optTranslator(DjlPairTranslator(modelPath))
                .build()
        model = criteria.loadModel()
        predictor = model.newPredictor()
    }

    override suspend fun rank(
        query: String,
        documents: List<String>,
    ): List<RerankingService.ScoredDocument> =
        documents.chunked(chunkSize).flatMap { chunk ->
            val pairs = chunk.map { "$query [SEP] $it" }
            val scores = predictor.batchPredict(pairs).toList()
            chunk
                .mapIndexed { idx, doc ->
                    RerankingService.ScoredDocument(document = doc, score = scores[idx])
                }
        }

    override fun close() {
        predictor.close()
        model.close()
    }
}
