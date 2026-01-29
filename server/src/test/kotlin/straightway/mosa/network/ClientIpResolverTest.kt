package straightway.mosa.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ClientIpResolverTest {
    @Test
    fun `returns remote host when proxy not trusted`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1"))

        val resolved = resolver.resolve(remoteHost = "192.168.1.10", forwardedFor = "203.0.113.7")

        assertEquals("192.168.1.10", resolved)
    }

    @Test
    fun `returns first forwarded ip when proxy trusted`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1"))

        val resolved = resolver.resolve(remoteHost = "10.0.0.1", forwardedFor = "203.0.113.7, 198.51.100.2")

        assertEquals("203.0.113.7", resolved)
    }

    @Test
    fun `falls back to remote host when forwarded header blank`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1"))

        val resolved = resolver.resolve(remoteHost = "10.0.0.1", forwardedFor = "   ")

        assertEquals("10.0.0.1", resolved)
    }

    @Test
    fun `returns remote host when forwarded header missing`() {
        val resolver = ClientIpResolver(setOf("10.0.0.1"))

        val resolved = resolver.resolve(remoteHost = "10.0.0.1", forwardedFor = null)

        assertEquals("10.0.0.1", resolved)
    }
}
