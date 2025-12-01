package straightway.mosa.commandline

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.BufferedReader

class PostDumpStreamIO(
    private val input: BufferedReader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StreamReader<VectorStore.PostDump> {
    override suspend fun readNext(): VectorStore.PostDump? = withContext(ioDispatcher) { input.readLine() }?.let { parseLine(it) }

    private fun parseLine(nextLine: String): VectorStore.PostDump =
        runCatching {
            val (id, receivedAt, authorGid, rootId, text) =
                nextLine.split(',', limit = 5)
            VectorStore.PostDump(
                id.trim(),
                text.unescape(),
                mapOf(
                    AUTHOR_GID_KEY to authorGid.trim(),
                    RECEIVED_AT_KEY to receivedAt.trim(),
                ) + (if (rootId.isBlank()) emptyMap() else mapOf(ROOT_ID_KEY to rootId.trim())),
            )
        }.onFailure {
            throw RuntimeException("Could not parse line '$nextLine'", it)
        }.getOrThrow()

    private fun String.unescape() =
        replace(Regex("""(?<!\\)\\n"""), "\n")
            .replace("""\\""", """\""")
}
