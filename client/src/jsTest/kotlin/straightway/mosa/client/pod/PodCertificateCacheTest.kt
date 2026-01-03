package straightway.mosa.client.pod

import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PodCertificateCacheTest {
    @Test
    fun `returns cached certificate until expiration`() = runTest {
        val time = TestTimeService(1000)
        val store = InMemoryPodCertificateStore()
        val cache = PodCertificateCache(time, store)
        val certificate =
            PodCertificateResponse(
                certificate = PodCertificate(gid = "gid-1", exp = 1200, sig = "sig", inf = null),
                refreshRecommendedAt = 1100,
            )

        cache.store("https://pod.example", "gid-1", certificate)

        assertEquals(certificate, cache.cached("https://pod.example", "gid-1"))

        time.nowValue = 1201

        assertNull(cache.cached("https://pod.example", "gid-1"))
    }

    @Test
    fun `loads certificate from store after reload`() = runTest {
        val time = TestTimeService(1000)
        val store = InMemoryPodCertificateStore()
        val certificate =
            PodCertificateResponse(
                certificate = PodCertificate(gid = "gid-1", exp = 1200, sig = "sig", inf = null),
                refreshRecommendedAt = 1100,
            )
        val firstCache = PodCertificateCache(time, store)

        firstCache.store("https://pod.example", "gid-1", certificate)

        val reloadedCache = PodCertificateCache(time, store)

        assertEquals(certificate, reloadedCache.cached("https://pod.example", "gid-1"))
    }

    @Test
    fun `stores certificates per pod and gid`() = runTest {
        val time = TestTimeService(1000)
        val store = RecordingPodCertificateStore()
        val cache = PodCertificateCache(time, store)
        val certificate =
            PodCertificateResponse(
                certificate = PodCertificate(gid = "gid-1", exp = 1200, sig = "sig", inf = null),
                refreshRecommendedAt = 1100,
            )

        cache.store("https://pod-a.example", "gid-1", certificate)
        cache.store(
            "https://pod-b.example",
            "gid-2",
            certificate.copy(certificate = certificate.certificate.copy(gid = "gid-2")),
        )

        assertEquals(
            listOf(
                "store:https://pod-a.example:gid-1",
                "store:https://pod-b.example:gid-2",
            ),
            store.calls,
        )
    }

    @Test
    fun `removes certificates per pod and gid`() = runTest {
        val time = TestTimeService(1000)
        val store = RecordingPodCertificateStore()
        val cache = PodCertificateCache(time, store)

        cache.invalidate("https://pod-a.example", "gid-1")

        assertEquals(listOf("remove:https://pod-a.example:gid-1"), store.calls)
    }
}

private class RecordingPodCertificateStore : PodCertificateStore {
    val calls = mutableListOf<String>()

    override fun load(podBase: String, gid: String): PodCertificateResponse? {
        calls += "load:$podBase:$gid"
        return null
    }

    override fun store(podBase: String, gid: String, certificate: PodCertificateResponse) {
        calls += "store:$podBase:$gid"
    }

    override fun remove(podBase: String, gid: String) {
        calls += "remove:$podBase:$gid"
    }
}
