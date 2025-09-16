package straightway.mosa.client

import kotlinx.coroutines.test.runTest
import org.koin.core.component.KoinComponent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityTest : KoinComponent {

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @BeforeTest
    fun startDI() {
        ClientDI.start()
    }

    @Test
    fun `generate identity provides gid and keys`() = runTest {
        val id = identityGenerator.generate()

        assertEquals(32, cryptoExport.pubRawOf(id.keyPair).length)
        assertEquals(43, id.gid.length)
        assertTrue(cryptoExport.privPkcs8Of(id.keyPair).length > 0)
    }
}
