package straightway.mosa.commandline

import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.HasherFactory
import straightway.mosa.VectorStore
import straightway.mosa.configureMosaAppModule
import straightway.mosa.content.ContentService
import straightway.mosa.content.ContentService.RootNotFoundException
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess

@OptIn(ExperimentalCli::class)
internal class ImportCommand(
    private val streamIO: PostDumpStreamIO,
    private val input: InputStream,
    private val exit: (Int) -> Nothing = ::exitProcess,
    private val hasherFactory: HasherFactory,
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
        val authorGid = post.getAuthorGid()
        val authorPublicKey = post.requireField(AUTHOR_PUBLIC_KEY_KEY)
        val publishRequest = post.toPublishRequest()
        val rootId = post.fields[ROOT_ID_KEY]
        val receiveTimestamp = post.fields[RECEIVED_AT_KEY]?.toLong() ?: 0L
        val id =
            try {
                if (rootId == null) {
                    publish(authorGid, publishRequest, receiveTimestamp, authorPublicKey)
                } else {
                    val realRootId = idTable.getOrDefault(rootId, rootId)
                    reply(
                        authorGid,
                        ReplyRequest(
                            publishRequest,
                            realRootId,
                        ),
                        receiveTimestamp,
                        authorPublicKey,
                    )
                }
            } catch (_: RootNotFoundException) {
                return ImportState.Failure
            }

        if (id != post.id) idTable[post.id] = id
        return ImportState.Success
    }

    private fun VectorStore.PostDump.getAuthorGid(): String {
        val authorGid = fields[AUTHOR_GID_KEY] ?: throw RuntimeException("user without field value for `${AUTHOR_GID_KEY}`")
        return normalizeAuthorGid(authorGid)
    }

    private fun VectorStore.PostDump.toPublishRequest(): PublishRequest =
        PublishRequest(
            text = text,
            signature = requireField(AUTHOR_SIGNATURE_KEY),
            signatureVersion = requireField(SIGNATURE_VERSION_KEY).toInt(),
        )

    private fun VectorStore.PostDump.requireField(fieldName: String): String =
        fields[fieldName] ?: throw RuntimeException("user without field value for `$fieldName`")

    private fun normalizeAuthorGid(authorGid: String): String =
        if (isBase64Url(authorGid)) {
            authorGid
        } else {
            val hasher = hasherFactory()
            val hash = hasher.digest(authorGid.toByteArray())
            Base64.UrlSafe.encode(hash)
        }

    private fun isBase64Url(value: String): Boolean =
        runCatching {
            Base64.UrlSafe.decode(value)
        }.isSuccess
}
