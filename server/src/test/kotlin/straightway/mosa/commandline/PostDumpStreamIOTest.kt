package straightway.mosa.commandline

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull

class PostDumpStreamIOTest {
    @Test
    fun `readNext reads with empty stream returns null`() =
        runTest("") { sut ->
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext reads first complete line with root ID`() =
        runTest("id,1234,authorGid,rootId,This,is,the,text") { sut ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    "This,is,the,text",
                    mapOf(
                        ROOT_ID_KEY to "rootId",
                        AUTHOR_GID_KEY to "authorGid",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext trims whitespaces from anything but the text`() =
        runTest(" id , 1234 , authorGid , rootId , This,is,the,text ") { sut ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    " This,is,the,text ",
                    mapOf(
                        ROOT_ID_KEY to "rootId",
                        AUTHOR_GID_KEY to "authorGid",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext unescapes the text`() =
        runTest("""id,1234,authorGid,rootId,FirstLine\nNewLine\\nSameLine""") { sut ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    "FirstLine\nNewLine\\nSameLine",
                    mapOf(
                        ROOT_ID_KEY to "rootId",
                        AUTHOR_GID_KEY to "authorGid",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext with too few arguments throws`() =
        runTest("id,1234,authorGid,rootId") { sut ->
            val ex = assertFails { sut.readNext() }
            assertIs<RuntimeException>(ex)
            assertEquals("Could not parse line 'id,1234,authorGid,rootId'", ex.message)
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext of regular line after previous line with error works`() =
        runTest("id,1234,authorGid,rootId\nid,1234,authorGid, ,This,is,the,text") { sut ->
            assertFails { sut.readNext() }
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    "This,is,the,text",
                    mapOf(
                        AUTHOR_GID_KEY to "authorGid",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext reads first complete line without root ID`() =
        runTest("id,1234,authorGid,,This,is,the,text") { sut ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    "This,is,the,text",
                    mapOf(
                        AUTHOR_GID_KEY to "authorGid",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext reads two complete lines`() =
        runTest("id1,1234,authorGid1,,This,is,the,text\nid2,2345,authorGid2,rootId,Second text") { sut ->
            assertEquals(
                VectorStore.PostDump(
                    "id1",
                    "This,is,the,text",
                    mapOf(
                        AUTHOR_GID_KEY to "authorGid1",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                sut.readNext(),
            )
            assertEquals(
                VectorStore.PostDump(
                    "id2",
                    "Second text",
                    mapOf(
                        ROOT_ID_KEY to "rootId",
                        AUTHOR_GID_KEY to "authorGid2",
                        RECEIVED_AT_KEY to "2345",
                    ),
                ),
                sut.readNext(),
            )
            assertNull(sut.readNext())
        }

    @Test
    fun `readNext decodes backslash before newline`() =
        runTest(
            "id,1234,author,,backslash before newline\\\\\\nnext line",
        ) { reader ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    "backslash before newline\\\nnext line",
                    mapOf(
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                reader.readNext(),
            )
        }

    @Test
    fun `readNext preserves literal backslash n`() =
        runTest(
            "id,1234,author,,literal \\\\n stays literal",
        ) { reader ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    """literal \n stays literal""",
                    mapOf(
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                reader.readNext(),
            )
        }

    @Test
    fun `readNext preserves double backslash before n`() =
        runTest(
            "id,1234,author,,two backslashes then n: \\\\\\\\n",
        ) { reader ->
            assertEquals(
                VectorStore.PostDump(
                    "id",
                    """two backslashes then n: \\n""",
                    mapOf(
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                ),
                reader.readNext(),
            )
        }

    @Test
    fun `asFlow yields sequence of two complete lines`() =
        runTest("id1,1234,authorGid1,,This,is,the,text\nid2,2345,authorGid2,rootId,Second text") { sut ->
            assertEquals(
                listOf(
                    VectorStore.PostDump(
                        "id1",
                        "This,is,the,text",
                        mapOf(
                            AUTHOR_GID_KEY to "authorGid1",
                            RECEIVED_AT_KEY to "1234",
                        ),
                    ),
                    VectorStore.PostDump(
                        "id2",
                        "Second text",
                        mapOf(
                            ROOT_ID_KEY to "rootId",
                            AUTHOR_GID_KEY to "authorGid2",
                            RECEIVED_AT_KEY to "2345",
                        ),
                    ),
                ),
                sut.asFlow().toList(),
            )
        }

    @Test
    fun `asFlow stops on first error`() =
        runTest("id1,1234,authorGid1,,This,is,the,text\nerror\nid2,2345,authorGid2,rootId,Second text") { sut ->
            val flow = sut.asFlow()
            val collected = mutableListOf<VectorStore.PostDump>()
            val ex = assertFails { flow.toList(collected) }
            assertIs<RuntimeException>(ex)
            assertEquals("Could not parse line 'error'", ex.message)
            assertEquals(
                listOf(
                    VectorStore.PostDump(
                        "id1",
                        "This,is,the,text",
                        mapOf(
                            AUTHOR_GID_KEY to "authorGid1",
                            RECEIVED_AT_KEY to "1234",
                        ),
                    ),
                ),
                collected,
            )
            assertEquals(
                listOf(
                    VectorStore.PostDump(
                        "id2",
                        "Second text",
                        mapOf(
                            ROOT_ID_KEY to "rootId",
                            AUTHOR_GID_KEY to "authorGid2",
                            RECEIVED_AT_KEY to "2345",
                        ),
                    ),
                ),
                flow.toList(),
            )
        }

    @Test
    fun `writerTo writes full PostDump`() =
        runTest {
            val postDump =
                VectorStore.PostDump(
                    "id",
                    "Text",
                    mapOf(
                        ROOT_ID_KEY to "rootId",
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                )
            val sut = PostDumpStreamIO()
            val written =
                ByteArrayOutputStream()
                    .use { output ->
                        output.bufferedWriter().use { writer ->
                            sut.writerTo(writer).write(postDump)
                        }
                        output.toByteArray()
                    }.toString(Charsets.UTF_8)

            assertEquals("id,1234,author,rootId,Text\n", written)
        }

    @Test
    fun `writerTo escapes text`() =
        runTest {
            val postDump =
                VectorStore.PostDump(
                    "id",
                    "First\\\\Line\nSecond,line",
                    mapOf(
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                )
            val sut = PostDumpStreamIO()
            val written =
                ByteArrayOutputStream()
                    .use { output ->
                        output.bufferedWriter().use { writer ->
                            sut.writerTo(writer).write(postDump)
                        }
                        output.toByteArray()
                    }.toString(Charsets.UTF_8)

            assertEquals("id,1234,author,,First\\\\\\\\Line\\nSecond,line\n", written)
        }

    @Test
    fun `writerTo leaves flushing to caller`() =
        runTest {
            val writer =
                CountingWriter().let {
                    BufferedWriter(it) to it
                }
            val postDump =
                VectorStore.PostDump(
                    "id",
                    "text",
                    mapOf(
                        AUTHOR_GID_KEY to "author",
                        RECEIVED_AT_KEY to "1234",
                    ),
                )
            try {
                PostDumpStreamIO().writerTo(writer.first).write(postDump)

                assertEquals(0, writer.second.flushCount)

                writer.first.flush()

                assertEquals("id,1234,author,,text\n", writer.second.written())
                assertEquals(1, writer.second.flushCount)
            } finally {
                writer.first.close()
            }
        }

    @Test
    fun `writerTo writes dumps readable by readerFrom`() =
        runTest {
            val dumps =
                listOf(
                    VectorStore.PostDump(
                        "id1",
                        "First text",
                        mapOf(
                            AUTHOR_GID_KEY to "author-1",
                            RECEIVED_AT_KEY to "1",
                        ),
                    ),
                    VectorStore.PostDump(
                        "id2",
                        "Second text",
                        mapOf(
                            ROOT_ID_KEY to "root-id",
                            AUTHOR_GID_KEY to "author-2",
                            RECEIVED_AT_KEY to "2",
                        ),
                    ),
                )
            val sut = PostDumpStreamIO()
            val written =
                ByteArrayOutputStream().use { output ->
                    output.bufferedWriter().use { writer ->
                        val streamWriter = sut.writerTo(writer)
                        dumps.forEach { streamWriter.write(it) }
                    }
                    output.toByteArray()
                }

            val parsed =
                written.inputStream().bufferedReader().use { reader ->
                    val readerStream = sut.readerFrom(reader)
                    buildList {
                        var next = readerStream.readNext()
                        while (next != null) {
                            add(next)
                            next = readerStream.readNext()
                        }
                    }
                }

            assertEquals(dumps, parsed)
        }

    @Test
    fun `writerTo fails when receivedAt metadata missing`() =
        runTest {
            val postDump =
                VectorStore.PostDump(
                    "id",
                    "Text",
                    mapOf(AUTHOR_GID_KEY to "author"),
                )
            val writer = PostDumpStreamIO().writerTo(ByteArrayOutputStream().bufferedWriter())

            val ex = assertFails { writer.write(postDump) }

            assertIs<IllegalStateException>(ex)
            assertEquals("Missing received-at metadata", ex.message)
        }

    @Test
    fun `writerTo fails when author metadata missing`() =
        runTest {
            val postDump =
                VectorStore.PostDump(
                    "id",
                    "Text",
                    mapOf(RECEIVED_AT_KEY to "1234"),
                )
            val writer = PostDumpStreamIO().writerTo(ByteArrayOutputStream().bufferedWriter())

            val ex = assertFails { writer.write(postDump) }

            assertIs<IllegalStateException>(ex)
            assertEquals("Missing author GID metadata", ex.message)
        }

    @Test
    fun `writeAll writes complete flow`() =
        runTest {
            val dumps =
                listOf(
                    VectorStore.PostDump(
                        "id1",
                        "First text",
                        mapOf(
                            AUTHOR_GID_KEY to "author-1",
                            RECEIVED_AT_KEY to "1",
                        ),
                    ),
                    VectorStore.PostDump(
                        "id2",
                        "Second text",
                        mapOf(
                            ROOT_ID_KEY to "root-id",
                            AUTHOR_GID_KEY to "author-2",
                            RECEIVED_AT_KEY to "2",
                        ),
                    ),
                )
            val output =
                ByteArrayOutputStream().use { stream ->
                    stream.bufferedWriter().use { writer ->
                        PostDumpStreamIO().writerTo(writer).writeAll(dumps.asFlow())
                    }
                    stream.toByteArray()
                }

            val parsed =
                output.inputStream().bufferedReader().use { reader ->
                    val readerStream = PostDumpStreamIO().readerFrom(reader)
                    buildList {
                        var next = readerStream.readNext()
                        while (next != null) {
                            add(next)
                            next = readerStream.readNext()
                        }
                    }
                }

            assertEquals(dumps, parsed)
        }

    private fun runTest(
        input: String,
        testBody: suspend (StreamReader<VectorStore.PostDump>) -> Unit,
    ) = runTest {
        input.byteInputStream().use { stream ->
            stream.bufferedReader().use { bufferedStream ->
                val sut = PostDumpStreamIO()
                testBody(sut.readerFrom(bufferedStream))
            }
        }
    }

    private class CountingWriter : Writer() {
        private val target = StringBuilder()
        var flushCount = 0
            private set

        override fun write(
            cbuf: CharArray,
            off: Int,
            len: Int,
        ) {
            target.append(cbuf, off, len)
        }

        override fun flush() {
            flushCount++
        }

        override fun close() {}

        fun written(): String = target.toString()
    }
}
