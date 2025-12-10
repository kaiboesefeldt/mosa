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
    private val fromTime by option(type = ArgType.String, fullName = "from", shortName = "f")
    private val toTime by option(type = ArgType.String, fullName = "until", shortName = "u")
    private val minScore by option(type = ArgType.Double, fullName = "score-above", shortName = "s")

    override fun execute() {
        runBlocking {
            val response =
                restClient().search(
                    QueryRequest(
                        topic = topic,
                        fromTime = fromTime?.let { Instant.parse(it).epochSecond },
                        toTime = toTime?.let { Instant.parse(it).epochSecond },
                        threadRootId = rootId,
                        scoreMustBeAbove = minScore?.toFloat(),
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
                                "stored: ${Instant.ofEpochSecond(it.storedAt)}",
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
