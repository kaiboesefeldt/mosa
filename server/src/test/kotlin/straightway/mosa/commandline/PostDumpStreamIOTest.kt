package straightway.mosa.commandline

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
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

    private fun runTest(
        input: String,
        testBody: suspend (PostDumpStreamIO) -> Unit,
    ) = runTest {
        input.byteInputStream().use { stream ->
            stream.bufferedReader().use { bufferedStream ->
                val sut = PostDumpStreamIO(bufferedStream)
                testBody(sut)
            }
        }
    }
}
