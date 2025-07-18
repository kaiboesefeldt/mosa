package straightway.mosa.testdata

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.use

data class TestPublish(
    val topic: String,
    val lang: Locale,
    val text: String,
) {
    companion object {
        fun load(): Map<String, List<TestPublish>> {
            val resourceUrl = TestPublish::class.java.classLoader.getResource("testContent")
            requireNotNull(resourceUrl) { "Resource directory not found!" }
            val path = Paths.get(resourceUrl.toURI())
            val mapper = jacksonObjectMapper()
            val published = mutableMapOf<String, List<TestPublish>>()
            processResourceFilesInPath(path) { fileContent ->
                val publishedTopic = mapper.readValue(fileContent, object : TypeReference<List<TestPublish>>() {})
                publishedTopic.forEach { publishedItem ->
                    val existingTopicItems = published[publishedItem.topic] ?: emptyList()
                    published[publishedItem.topic] = existingTopicItems + listOf(publishedItem)
                }
            }
            return published
        }

        private fun processResourceFilesInPath(
            path: Path,
            block: (String) -> Unit,
        ): Unit =
            Files.list(path).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .forEach { file ->
                        Files.newBufferedReader(file).use { reader ->
                            block(reader.readText())
                        }
                    }
            }
    }
}
