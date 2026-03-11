package straightway.mosa.commandline

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import java.io.BufferedReader
import java.io.BufferedWriter

class PostDumpStreamIO(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun readerFrom(input: BufferedReader): StreamReader<VectorStore.PostDump> =
        StreamReader {
            withContext(ioDispatcher) {
                input.readNonCommentLine()
            }?.let { parseLine(it) }
        }

    fun writerTo(output: BufferedWriter): StreamWriter<VectorStore.PostDump> {
        output.appendLine(HEADER_LINE)
        return StreamWriter {
            withContext(ioDispatcher) {
                output.appendLine(generateLine((it)))
            }
        }
    }

    private tailrec fun BufferedReader.readNonCommentLine(): String? {
        val nextLine = readLine()
        if (nextLine?.shouldLineBeSkipped() != true) return nextLine
        return readNonCommentLine()
    }

    private fun String.shouldLineBeSkipped() = startsWith(COMMENT_PREFIX) || isBlank()

    private fun generateLine(postDump: VectorStore.PostDump): String {
        val receivedAt = postDump.fields[RECEIVED_AT_KEY] ?: error("Missing received-at metadata")
        val authorGid = postDump.fields[AUTHOR_GID_KEY] ?: error("Missing author GID metadata")
        val authorPublicKey = postDump.fields[AUTHOR_PUBLIC_KEY_KEY] ?: error("Missing author public key metadata")
        val authorSignature = postDump.fields[AUTHOR_SIGNATURE_KEY] ?: error("Missing author signature metadata")
        val signatureVersion = postDump.fields[SIGNATURE_VERSION_KEY] ?: error("Missing signature version metadata")
        val rootId = postDump.fields[ROOT_ID_KEY].orEmpty()
        return "${postDump.id},$receivedAt,$authorGid,$authorPublicKey,$authorSignature,$signatureVersion,$rootId,${postDump.text.escape()}"
    }

    private fun parseLine(nextLine: String): VectorStore.PostDump =
        runCatching {
            when {
                isCurrentLine(nextLine) -> parseCurrentLine(nextLine)
                isLegacyLine(nextLine) -> parseLegacyLine(nextLine.split(',', limit = LEGACY_COLUMN_COUNT))
                else -> error("Unexpected column count")
            }
        }.onFailure {
            throw RuntimeException("Could not parse line '$nextLine'", it)
        }.getOrThrow()

    private fun isLegacyLine(nextLine: String): Boolean = nextLine.split(',', limit = LEGACY_COLUMN_COUNT).size == LEGACY_COLUMN_COUNT

    private fun isCurrentLine(nextLine: String): Boolean {
        val cells = nextLine.split(',', limit = CURRENT_COLUMN_COUNT)
        return cells.size == CURRENT_COLUMN_COUNT && cells[5].trim().toIntOrNull() != null
    }

    private fun parseLegacyLine(cells: List<String>): VectorStore.PostDump {
        val (id, receivedAt, authorGid, rootId, text) = cells
        return VectorStore.PostDump(
            id.trim(),
            text.unescape(),
            mapOf(
                AUTHOR_GID_KEY to authorGid.trim(),
                RECEIVED_AT_KEY to receivedAt.trim(),
            ) + (if (rootId.isBlank()) emptyMap() else mapOf(ROOT_ID_KEY to rootId.trim())),
        )
    }

    private fun parseCurrentLine(nextLine: String): VectorStore.PostDump {
        val cells = nextLine.split(',', limit = CURRENT_COLUMN_COUNT)
        val id = cells[0]
        val receivedAt = cells[1]
        val authorGid = cells[2]
        val authorPublicKey = cells[3]
        val authorSignature = cells[4]
        val signatureVersion = cells[5]
        val rootId = cells[6]
        val text = cells[7]
        return VectorStore.PostDump(
            id.trim(),
            text.unescape(),
            mapOf(
                AUTHOR_GID_KEY to authorGid.trim(),
                AUTHOR_PUBLIC_KEY_KEY to authorPublicKey.trim(),
                AUTHOR_SIGNATURE_KEY to authorSignature.trim(),
                SIGNATURE_VERSION_KEY to signatureVersion.trim(),
                RECEIVED_AT_KEY to receivedAt.trim(),
            ) + (if (rootId.isBlank()) emptyMap() else mapOf(ROOT_ID_KEY to rootId.trim())),
        )
    }

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

    companion object {
        internal const val HEADER_LINE =
            "# columns: id=post id, receivedAt=epoch seconds when received, authorGid=author gid, " +
                "authorPublicKey=author public key, authorSignature=content signature, " +
                "signatureVersion=content signature version, rootId=root post id or blank for roots, text=content encoded with \\n for " +
                "newline and \\\\ for backslash"
        private const val COMMENT_PREFIX = "#"
        private const val LEGACY_COLUMN_COUNT = 5
        private const val CURRENT_COLUMN_COUNT = 8
    }
}
