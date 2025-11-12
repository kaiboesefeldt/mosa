package straightway.mosa.client.util

import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.text.encodeToByteArray

class TextEncoderTest {
    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `encodes ASCII input`() {
        val encoded = textEncode("MOSA")
        assertContentEquals("MOSA".encodeToByteArray(), encoded)
    }

    @Test
    fun `encodes multibyte characters`() {
        val encoded = textEncode("äöü")
        assertContentEquals("äöü".encodeToByteArray(), encoded)
    }
}
