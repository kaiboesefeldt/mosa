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
import straightway.mosa.content.ContentService.RootNotFoundException
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
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
            runBlocking {
                val idTable = mutableMapOf<String, String>()
                val postponed = mutableListOf<VectorStore.PostDump>()
                input.bufferedReader().use { stdin ->
                    val reader = streamIO.readerFrom(stdin)
                    reader.asFlow().collect { postDump ->
                        if (contentService.import(postDump, idTable) == ImportState.Failure) {
                            postponed.add(postDump)
                        }
                    }
                }
                var lastPostponedSize = Int.MAX_VALUE
                while (postponed.size < lastPostponedSize) {
                    lastPostponedSize = postponed.size
                    val p = postponed.toList()
                    postponed.clear()
                    p.forEach { postDump ->
                        if (contentService.import(postDump, idTable) == ImportState.Failure) {
                            postponed.add(postDump)
                        }
                    }
                }
                check(postponed.isEmpty()) { "Could not import these posts: $postponed" }
            }
        } finally {
            koinApplication.close()
        }
        exit(0)
    }

    private enum class ImportState { Success, Failure }

    private suspend fun ContentService.import(
        post: VectorStore.PostDump,
        idTable: MutableMap<String, String>,
    ): ImportState {
        val authorGid =
            post.fields[AUTHOR_GID_KEY]
                ?: throw RuntimeException("user without field value for `${AUTHOR_GID_KEY}`")
        val rootId = post.fields[ROOT_ID_KEY]
        val receiveTimestamp = post.fields[RECEIVED_AT_KEY]?.toLong() ?: 0L
        val id =
            try {
                if (rootId == null) {
                    publish(authorGid, PublishRequest(post.text), receiveTimestamp)
                } else {
                    val realRootId = idTable.getOrDefault(rootId, rootId)
                    reply(
                        authorGid,
                        ReplyRequest(
                            PublishRequest(post.text),
                            realRootId,
                        ),
                        receiveTimestamp,
                    )
                }
            } catch (_: RootNotFoundException) {
                return ImportState.Failure
            }

        if (id != post.id) idTable[post.id] = id
        return ImportState.Success
    }
}
