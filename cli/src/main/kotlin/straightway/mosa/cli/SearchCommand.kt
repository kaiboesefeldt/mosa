package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import java.time.Instant

@OptIn(ExperimentalCli::class)
class SearchCommand(
    val restClient: () -> MosaRestClient,
) : Subcommand("search", "search for mosa content") {
    private val topic by option(type = ArgType.String, fullName = "topic", shortName = "t")
    private val rootId by option(type = ArgType.String, fullName = "root", shortName = "r")
    private val minTime by option(type = ArgType.String, fullName = "from", shortName = "f")

    override fun execute() {
        runBlocking {
            val response =
                restClient().search(
                    QueryRequest(
                        topic = topic,
                        threadRootId = rootId,
                        minTime = minTime?.let { Instant.parse(it) },
                    ),
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
                    val lines =
                        listOf(
                            "id: ${it.id}",
                            "content:\n${it.content.trimIndent().prependIndent("    ")}",
                        ) + (if (it.rootId === null) listOf() else listOf("rootId: ${it.rootId}")) +
                            listOf(
                                "similarity: ${it.similarity}",
                                "author: ${it.authorGID}",
                                "stored: ${it.storedAt}",
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
            """.trimMargin(),
        )
    }
}
