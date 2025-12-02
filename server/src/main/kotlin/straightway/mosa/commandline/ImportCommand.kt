package straightway.mosa.commandline

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
    private val streamIO: PostDumpStreamIO,
    private val input: InputStream,
    private val exit: (Int) -> Nothing = ::exitProcess,
) : Subcommand("import", "Import the mosa posts and replies from stdin") {
    override fun execute() {
        val koinApplication =
            startKoin {
                modules(module { configureMosaAppModule(CommandLineArgumentStore.EMPTY) })
            }
        val contentService: ContentService = koinApplication.koin.get()
        try {
            input.bufferedReader().use { stdin ->
                val reader = streamIO.readerFrom(stdin)
                runBlocking {
                    reader.asFlow().collect { contentService.import(it) }
                }
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }

    private suspend fun ContentService.import(post: VectorStore.PostDump) {
        val authorGid =
            post.fields[AUTHOR_GID_KEY]
                ?: throw RuntimeException("user without field value for `${AUTHOR_GID_KEY}`")
        val rootId = post.fields[ROOT_ID_KEY]
        if (rootId == null) {
            publish(authorGid, PublishRequest(post.text))
        } else {
            reply(authorGid, ReplyRequest(PublishRequest(post.text), rootId))
        }
    }
}
