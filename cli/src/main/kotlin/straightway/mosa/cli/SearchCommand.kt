package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import java.time.Instant

@OptIn(ExperimentalCli::class)
class SearchCommand(
    val restClient: () -> MosaRestClient,
) : Subcommand("search", "search for mosa content") {
    private val template by option(ArgType.String, fullName = "template", shortName = "t")
    private val embeddingVersion by option(ArgType.String, fullName = "embver").default("1")
    private val embeddingVectorJson by option(type = ArgType.String, fullName = "embedding", shortName = "e")
    private val maxResults by option(type = ArgType.Int, fullName = "results", shortName = "r").default(10)

    private val embeddingVector by lazy {
        if (embeddingVectorJson.isNullOrBlank()) {
            OnnxLocalEmbeddingService().use {
                runBlocking {
                    it.embed(
                        template
                            ?: throw IllegalArgumentException("You must either specify --template or --embedding")
                    )
                }.toList()
            }
        } else {
            embeddingVectorJson!!
                .replace("[", "")
                .replace("]", "")
                .replace(",", " ")
                .split(Regex("""\s+"""))
                .map { it.toFloat() }
        }
    }

    override fun execute() {
        runBlocking {
            val response = restClient().search(
                QueryRequest(
                    embeddingVersion = embeddingVersion,
                    embeddingVector = embeddingVector,
                    maxResults = maxResults
                )
            )
            prettyPrint(response)
        }
    }

    private fun prettyPrint(searchResult: QueryResult) {
        val searchResultEntriesString =
            if (searchResult.entries.isEmpty()) {
                "Empty"
            } else {
                searchResult.entries.joinToString {
                    val lines = listOf(
                        "id: ${it.id}",
                        "content:\n${it.content.trimIndent().prependIndent("    ")}"
                    ) + (if (it.parent === null) listOf() else listOf("parent: ${it.parent}")) + listOf(
                        "similarity: ${it.similarity}",
                        "author: ${it.authorGID}",
                        "stored: ${Instant.ofEpochSecond(it.storedAt)}"
                    )
                    val resultContent = lines.joinToString("\n")
                    "Search result entry\n${resultContent.prependIndent("    ")}"
                }
            }
        println(
            """
                |SearchResult
                |${searchResultEntriesString.prependIndent("    ")}
                |    ${if (searchResult.queryOnId.isBlank()) "" else "search on ID: ${searchResult.queryOnId}"}
            """.trimMargin()
        )
    }
}
