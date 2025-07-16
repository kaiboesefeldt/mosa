package straightway.mosa.reranking

import java.nio.file.Files.copy
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class HuggingfaceModelDownloader(
    val provider: String = "BAAI",
    val modelName: String = "bge-reranker-base",
    val modelFiles: List<String> =
        listOf(
            "onnx/model.onnx",
            "tokenizer.json",
            "tokenizer_config.json",
            "config.json",
        ),
) {
    private val baseUrl = "https://huggingface.co/$provider/$modelName/resolve/main/"

    fun download(modelsDir: Path = Paths.get("models")): Path {
        val targetPath = modelsDir.resolve(modelName)
        if (!targetPath.exists()) {
            createDirectories(targetPath)
        }
        modelFiles.forEach { file ->
            val url = "$baseUrl$file"
            val dest = targetPath.resolve(file)
            if (!dest.parent.exists()) {
                createDirectories(dest.parent)
            }
            if (!dest.exists()) {
                java.net.URI(url).toURL().openStream().use { input ->
                    copy(input, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        return targetPath
    }
}
