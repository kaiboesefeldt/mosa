package straightway.mosa.client.pod

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.pod.TestTimeService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PodCertificateCacheTest {
    @Test
    fun `returns cached certificate until expiration`() = runTest {
        val time = TestTimeService(1000)
        val cache = PodCertificateCache(time)
        val certificate = PodCertificate(gid = "gid-1", exp = 1200, sig = "sig", inf = null)

        cache.store("https://pod.example", "gid-1", certificate)

        assertEquals(certificate, cache.cached("https://pod.example", "gid-1"))

        time.nowValue = 1201

        assertNull(cache.cached("https://pod.example", "gid-1"))
    }
}
