package straightway.mosa.cli

import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking
import straightway.mosa.content.SearchRequest
import straightway.mosa.embedding.OnnxLocalEmbeddingService

@OptIn(ExperimentalCli::class)
class SearchCommand(
    val restClient: () -> MosaRestClient,
) : Subcommand("search", "search for mosa content") {
    private val template by option(ArgType.String, fullName = "template", shortName = "t")
    private val embeddingVersion by option(ArgType.String, fullName = "embver").default("1")
    private val embeddingVectorJson by option(type = ArgType.String, fullName = "embedding", shortName = "e")
    private val minSimilarity by option(type = ArgType.Double, fullName = "similarity", shortName = "s").default(0.8)
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
            val result = restClient().search(
                SearchRequest(
                    embeddingVersion = embeddingVersion,
                    embeddingVector = embeddingVector,
                    minSimilarity = minSimilarity.toFloat(),
                    maxResults = maxResults
                )
            )
        }
    }
}
