@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package straightway.mosa.client.identity

import kotlinx.coroutines.test.runTest
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import straightway.mosa.client.AppModule
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityGeneratorTest : KoinComponent {

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @BeforeTest
    fun startDI() {
        AppModule.start()
    }

    @Test
    fun `generate identity provides gid and keys`() = runTest {
        val id = this@IdentityGeneratorTest.get<IdentityGenerator>().generate()

        assertEquals(32, this@IdentityGeneratorTest.get<CryptoExport>().pubRawOf(id.keyPair).size)
        assertEquals(43, id.gid.length)
        assertTrue(this@IdentityGeneratorTest.get<CryptoExport>().privPkcs8Of(id.keyPair).size > 0)
    }
}
