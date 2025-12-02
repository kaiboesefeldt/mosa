package straightway.mosa.commandline

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.BufferedReader
import java.io.BufferedWriter

class PostDumpStreamIO(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun readerFrom(input: BufferedReader): StreamReader<VectorStore.PostDump> =
        StreamReader {
            withContext(ioDispatcher) { input.readLine() }?.let { parseLine(it) }
        }

    fun writerTo(output: BufferedWriter): StreamWriter<VectorStore.PostDump> =
        StreamWriter {
            withContext(ioDispatcher) {
                output.appendLine(generateLine((it)))
                output.flush()
            }
        }

    private fun generateLine(postDump: VectorStore.PostDump): String {
        val receivedAt = postDump.fields[RECEIVED_AT_KEY] ?: error("Missing received-at metadata")
        val authorGid = postDump.fields[AUTHOR_GID_KEY] ?: error("Missing author GID metadata")
        val rootId = postDump.fields[ROOT_ID_KEY].orEmpty()
        return "${postDump.id},$receivedAt,$authorGid,$rootId,${postDump.text.escape()}"
    }

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

    private fun String.escape(): String =
        buildString {
            for (char in this@escape) {
                when (char) {
                    '\\' -> append("""\\""")
                    '\n' -> append("""\n""")
                    else -> append(char)
                }
            }
        }

    private fun String.unescape(): String =
        buildString {
            var index = 0
            while (index < this@unescape.length) {
                when {
                    this@unescape.startsWith("\\\\", index) -> {
                        append('\\')
                        index += 2
                    }

                    this@unescape.startsWith("\\n", index) -> {
                        append('\n')
                        index += 2
                    }

                    else -> {
                        append(this@unescape[index])
                        index += 1
                    }
                }
            }
        }
}
