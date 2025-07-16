import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.inference.Predictor
import ai.djl.ndarray.NDList
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext

// Hilfsfunktion zum Herunterladen des Modells
object ModelDownloader {
    fun downloadBGERerankerModel(targetDir: String = "./models/bge-reranker-base") {
        val modelFiles =
            listOf(
                "onnx/model.onnx",
                "tokenizer.json",
                "tokenizer_config.json",
                "config.json",
            )

        val baseUrl = "https://huggingface.co/BAAI/bge-reranker-base/resolve/main/"
        val targetPath =
            java.nio.file.Paths
                .get(targetDir)

        // Erstelle Verzeichnis falls nicht vorhanden
        if (!java.nio.file.Files
                .exists(targetPath)
        ) {
            java.nio.file.Files
                .createDirectories(targetPath)
        }

        println("Downloading BGE Reranker model to $targetDir...")

        modelFiles.forEach { file ->
            val url = "$baseUrl$file"
            val targetFile = targetPath.resolve(file)

            // Erstelle Unterverzeichnis falls nötig
            val parentDir = targetFile.parent
            if (!java.nio.file.Files
                    .exists(parentDir)
            ) {
                java.nio.file.Files
                    .createDirectories(parentDir)
            }

            try {
                println("Downloading $file...")
                val connection = java.net.URI(url).toURL().openConnection()
                connection.setRequestProperty("User-Agent", "DJL-BGE-Reranker")

                connection.getInputStream().use { input ->
                    java.nio.file.Files
                        .copy(input, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                println("Downloaded $file")
            } catch (e: Exception) {
                println("Error downloading $file: ${e.message}")
            }
        }

        println("Model download completed!")
    }
}

// Für lokales Modell - laden Sie das Modell zuerst herunter
const val MODEL_PATH = "./models/bge-reranker-base" // Lokaler Pfad zum Modell

// Oder direkt ONNX-Datei
const val ONNX_MODEL_PATH = "./models/bge-reranker-base/onnx/model.onnx"
const val MODEL_URL = "https://huggingface.co/BAAI/bge-reranker-base"
const val MAX_LENGTH = 512

fun main() {
    // Schritt 1: Modell herunterladen (nur einmal nötig)
    if (!java.nio.file.Files
            .exists(
                java.nio.file.Paths
                    .get(MODEL_PATH),
            )
    ) {
        ModelDownloader.downloadBGERerankerModel()
    }

    // Beispiel Query und Dokumente
    val query = "What is machine learning?"
    val documents =
        listOf(
            "Machine learning is a subset of artificial intelligence.",
            "Deep learning uses neural networks with multiple layers.",
            "Python is a programming language.",
            "Natural language processing deals with text understanding.",
        )

    val reranker = BGEReranker()
    val results = reranker.rerank(query, documents)

    // Ergebnisse ausgeben
    println("Reranking Results:")
    results.forEachIndexed { index, result ->
        println("${index + 1}. Score: %.4f - %s".format(result.score, result.document))
    }

    reranker.close()
}

class BGEReranker {
    private val model: ZooModel<Array<String>, FloatArray>
    private val predictor: Predictor<Array<String>, FloatArray>

    init {
        model = loadModel()
        predictor = model.newPredictor()
    }

    private fun loadModel(): ZooModel<Array<String>, FloatArray> {
        // Option 1: Lokales Modell-Verzeichnis
        val criteria =
            Criteria
                .builder()
                .setTypes(Array<String>::class.java, FloatArray::class.java)
                .optModelPath(
                    java.nio.file.Paths
                        .get(MODEL_PATH, "onnx"),
                ).optEngine("OnnxRuntime")
                .optTranslator(BGERerankerTranslator())
                .build()

        return criteria.loadModel()

        // Option 2: Direkte ONNX-Datei (alternative Implementierung)
        /*
        val criteria = Criteria.builder()
            .setTypes(Array<String>::class.java, FloatArray::class.java)
            .optModelPath(java.nio.file.Paths.get(ONNX_MODEL_PATH))
            .optEngine("OnnxRuntime")
            .optTranslator(BGERerankerTranslator())
            .build()

        return criteria.loadModel()
         */
    }

    fun rerank(
        query: String,
        documents: List<String>,
    ): List<RerankResult> {
        // Erstelle Query-Document Paare
        val pairs =
            documents
                .map { document ->
                    "$query [SEP] $document"
                }.toTypedArray()

        // Führe Inferenz durch
        val scores = predictor.predict(pairs)

        // Erstelle Ergebnisse und sortiere nach Score (absteigend)
        return documents
            .mapIndexed { index, document ->
                RerankResult(document, scores[index])
            }.sortedByDescending { it.score }
    }

    fun close() {
        predictor.close()
        model.close()
    }
}

class BGERerankerTranslator : Translator<Array<String>, FloatArray> {
    private lateinit var tokenizer: HuggingFaceTokenizer

    override fun prepare(ctx: TranslatorContext) {
        tokenizer =
            HuggingFaceTokenizer
                .builder()
                .optTokenizerPath(
                    java.nio.file.Paths
                        .get(MODEL_PATH, "tokenizer.json"),
                ).optPadding(true)
                .optTruncation(true)
                .optMaxLength(MAX_LENGTH)
                .build()
    }

    override fun processInput(
        ctx: TranslatorContext,
        input: Array<String>,
    ): NDList {
        val manager = ctx.ndManager

        // Tokenisiere alle Input-Paare
        val inputIds = mutableListOf<LongArray>()
        val attentionMask = mutableListOf<LongArray>()

        input.forEach { text ->
            val encoding = tokenizer.encode(text)
            inputIds.add(encoding.ids)
            attentionMask.add(encoding.attentionMask)
        }

        // Konvertiere zu NDArrays
        val inputIdsArray = manager.create(inputIds.toTypedArray())
        val attentionMaskArray = manager.create(attentionMask.toTypedArray())

        return NDList(inputIdsArray, attentionMaskArray)
    }

    override fun processOutput(
        ctx: TranslatorContext,
        list: NDList,
    ): FloatArray {
        val output = list.singletonOrThrow()

        // Annahme: Das Modell gibt Logits aus, die durch Sigmoid zu Scores werden
        // Sigmoid-Funktion: 1 / (1 + exp(-x))
        val manager = ctx.ndManager
        val negated = output.neg()
        val expNegated = negated.exp()
        val onePlusExp = expNegated.add(1.0f)
        val scores = manager.create(1.0f).div(onePlusExp)

        // Konvertiere zu FloatArray
        return scores.toFloatArray()
    }

    override fun getBatchifier(): Batchifier? = null
}

data class RerankResult(
    val document: String,
    val score: Float,
)

// Erweiterte Klasse für bessere Konfiguration
class BGERerankerConfig {
    var modelUrl: String = MODEL_URL
    var maxLength: Int = MAX_LENGTH
    var applyLogSoftmax: Boolean = false
    var temperature: Float = 1.0f

    companion object {
        fun builder() = BGERerankerConfig()
    }

    fun modelUrl(url: String) = apply { this.modelUrl = url }

    fun maxLength(length: Int) = apply { this.maxLength = length }

    fun applyLogSoftmax(apply: Boolean) = apply { this.applyLogSoftmax = apply }

    fun temperature(temp: Float) = apply { this.temperature = temp }
}

// Erweiterte Reranker-Klasse mit Konfiguration
class ConfigurableBGEReranker(
    private val config: BGERerankerConfig,
) {
    private val model: ZooModel<Array<String>, FloatArray>
    private val predictor: Predictor<Array<String>, FloatArray>

    init {
        model = loadModel()
        predictor = model.newPredictor()
    }

    private fun loadModel(): ZooModel<Array<String>, FloatArray> {
        // Option 1: Lokales Modell-Verzeichnis
        val criteria =
            Criteria
                .builder()
                .setTypes(Array<String>::class.java, FloatArray::class.java)
                .optModelPath(
                    java.nio.file.Paths
                        .get(config.modelUrl),
                ).optEngine("OnnxRuntime")
                .optTranslator(ConfigurableBGERerankerTranslator(config))
                .build()

        return criteria.loadModel()
    }

    fun rerank(
        query: String,
        documents: List<String>,
    ): List<RerankResult> {
        val pairs =
            documents
                .map { document ->
                    "$query [SEP] $document"
                }.toTypedArray()

        val scores = predictor.predict(pairs)

        return documents
            .mapIndexed { index, document ->
                RerankResult(document, scores[index])
            }.sortedByDescending { it.score }
    }

    fun rerankWithIndices(
        query: String,
        documents: List<String>,
    ): List<IndexedRerankResult> {
        val pairs =
            documents
                .map { document ->
                    "$query [SEP] $document"
                }.toTypedArray()

        val scores = predictor.predict(pairs)

        return documents
            .mapIndexed { index, document ->
                IndexedRerankResult(index, document, scores[index])
            }.sortedByDescending { it.score }
    }

    fun close() {
        predictor.close()
        model.close()
    }
}

class ConfigurableBGERerankerTranslator(
    private val config: BGERerankerConfig,
) : Translator<Array<String>, FloatArray> {
    private lateinit var tokenizer: HuggingFaceTokenizer

    override fun prepare(ctx: TranslatorContext) {
        tokenizer =
            HuggingFaceTokenizer
                .builder()
                .optPadding(true)
                .optTruncation(true)
                .optMaxLength(config.maxLength)
                .build()
    }

    override fun processInput(
        ctx: TranslatorContext,
        input: Array<String>,
    ): NDList {
        val manager = ctx.ndManager

        val inputIds = mutableListOf<LongArray>()
        val attentionMask = mutableListOf<LongArray>()

        input.forEach { text ->
            val encoding = tokenizer.encode(text)
            inputIds.add(encoding.ids)
            attentionMask.add(encoding.attentionMask)
        }

        val inputIdsArray = manager.create(inputIds.toTypedArray())
        val attentionMaskArray = manager.create(attentionMask.toTypedArray())

        return NDList(inputIdsArray, attentionMaskArray)
    }

    override fun processOutput(
        ctx: TranslatorContext,
        list: NDList,
    ): FloatArray {
        val output = list.singletonOrThrow()
        val manager = ctx.ndManager

        // Anwenden der Temperatur falls konfiguriert
        val scaledOutput =
            if (config.temperature != 1.0f) {
                output.div(config.temperature)
            } else {
                output
            }

        // Anwenden von LogSoftmax oder Sigmoid
        val scores =
            if (config.applyLogSoftmax) {
                scaledOutput.logSoftmax(0)
            } else {
                // Sigmoid manuell implementieren: 1 / (1 + exp(-x))
                val negated = scaledOutput.neg()
                val expNegated = negated.exp()
                val onePlusExp = expNegated.add(1.0f)
                manager.create(1.0f).div(onePlusExp)
            }

        return scores.toFloatArray()
    }

    override fun getBatchifier(): Batchifier = Batchifier.STACK
}

data class IndexedRerankResult(
    val originalIndex: Int,
    val document: String,
    val score: Float,
)

// Verwendungsbeispiel mit Konfiguration
fun advancedExample() {
    val config =
        BGERerankerConfig
            .builder()
            .modelUrl("https://huggingface.co/BAAI/bge-reranker-base")
            .maxLength(512)
            .temperature(1.0f)
            .applyLogSoftmax(false)

    val reranker = ConfigurableBGEReranker(config)

    val query = "machine learning algorithms"
    val documents =
        listOf(
            "Support vector machines are powerful classification algorithms.",
            "Random forests combine multiple decision trees.",
            "The weather is nice today.",
            "Neural networks can learn complex patterns.",
        )

    val results = reranker.rerankWithIndices(query, documents)

    println("Advanced Reranking Results:")
    results.forEach { result ->
        println("Original Index: ${result.originalIndex}, Score: %.4f - %s".format(result.score, result.document))
    }

    reranker.close()
}
