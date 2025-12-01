package straightway.mosa.commandline

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.VectorStore
import straightway.mosa.configureMosaAppModule
import straightway.mosa.content.ContentService
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.InputStream
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ImportCommand(
    private val input: InputStream = System.`in`,
    private val exit: (Int) -> Nothing = ::exitProcess,
) : Subcommand("import", "Import the mosa posts and replies from stdin") {
    override fun execute() {
        val koinApplication =
            startKoin {
                modules(module { configureMosaAppModule(CommandLineArgumentStore.EMPTY) })
            }
        val contentService: ContentService = koinApplication.koin.get()
        val objectMapper = jacksonObjectMapper()
        try {
            runBlocking {
                objectMapper.factory.createParser(input).use { parser ->
                    require(parser.nextToken() == JsonToken.START_ARRAY) { "Expected JSON array" }
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        val entry = objectMapper.readValue(parser, VectorStore.PostDump::class.java)
                        val authorGid =
                            entry.fields[AUTHOR_GID_KEY] ?: throw RuntimeException("user without field value for `$AUTHOR_GID_KEY`")
                        val rootId = entry.fields[ROOT_ID_KEY]
                        if (rootId == null) {
                            contentService.publish(authorGid, PublishRequest(entry.text))
                        } else {
                            contentService.reply(authorGid, ReplyRequest(PublishRequest(entry.text), rootId))
                        }
                    }
                }
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }
}
