package straightway.mosa.config

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientBlockListTest {
    @Test
    fun `parses yaml into blocked clients`() {
        val yaml =
            """
            blockedClients:
              - "192.168.0.1"
              - "10.0.0.0/8"
              - "2001:db8::1"
              - "2001:db8::/32"
            """.trimIndent()

        val result = ClientBlockList.transformer.transform(yaml.encodeToByteArray())

        val expected =
            listOf(
                ClientBlockList.BlockedClient(InetAddress.getByName("192.168.0.1"), 32),
                ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 8),
                ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::1"), 128),
                ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::"), 32),
            )

        assertEquals(expected, result.blockedClients)
    }

    @Test
    fun `returns empty list for empty yaml`() {
        val result = ClientBlockList.transformer.transform(" \n".encodeToByteArray())

        assertEquals(emptyList(), result.blockedClients)
    }

    @Test
    fun `returns empty list when blockedClients is missing`() {
        val result = ClientBlockList.transformer.transform("{}".encodeToByteArray())

        assertEquals(emptyList(), result.blockedClients)
    }

    @Test
    fun `trims whitespace around entries`() {
        val yaml =
            """
            blockedClients:
              - "  192.168.0.1  "
            """.trimIndent()

        val result = ClientBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(
            listOf(ClientBlockList.BlockedClient(InetAddress.getByName("192.168.0.1"), 32)),
            result.blockedClients,
        )
    }

    @Test
    fun `defaults ipv4 prefix length when missing`() {
        val yaml =
            """
            blockedClients:
              - "10.0.0.1"
            """.trimIndent()

        val result = ClientBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(
            listOf(ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.1"), 32)),
            result.blockedClients,
        )
    }

    @Test
    fun `defaults ipv6 prefix length when missing`() {
        val yaml =
            """
            blockedClients:
              - "2001:db8::1"
            """.trimIndent()

        val result = ClientBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(
            listOf(ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::1"), 128)),
            result.blockedClients,
        )
    }

    @Test
    fun `accepts blank prefix as full length`() {
        val yaml =
            """
            blockedClients:
              - "10.0.0.1/ "
            """.trimIndent()

        val result = ClientBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(
            listOf(ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.1"), 32)),
            result.blockedClients,
        )
    }

    @Test
    fun `rejects invalid ip address`() {
        val yaml =
            """
            blockedClients:
              - "not-an-ip"
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ClientBlockList.transformer.transform(yaml.encodeToByteArray())
        }
    }

    @Test
    fun `rejects blank entry`() {
        val yaml =
            """
            blockedClients:
              - "   "
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ClientBlockList.transformer.transform(yaml.encodeToByteArray())
        }
    }

    @Test
    fun `rejects non-numeric prefix length`() {
        val yaml =
            """
            blockedClients:
              - "10.0.0.1/abc"
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ClientBlockList.transformer.transform(yaml.encodeToByteArray())
        }
    }

    @Test
    fun `rejects ipv4 prefix length out of range`() {
        val yaml =
            """
            blockedClients:
              - "10.0.0.1/33"
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ClientBlockList.transformer.transform(yaml.encodeToByteArray())
        }
    }

    @Test
    fun `rejects ipv6 prefix length out of range`() {
        val yaml =
            """
            blockedClients:
              - "2001:db8::/129"
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ClientBlockList.transformer.transform(yaml.encodeToByteArray())
        }
    }

    @Test
    fun `matches ipv4 subnet`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 8)

        assertEquals(true, blocked.matches(InetAddress.getByName("10.1.2.3")))
    }

    @Test
    fun `does not match ipv4 outside subnet`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 8)

        assertEquals(false, blocked.matches(InetAddress.getByName("11.0.0.1")))
    }

    @Test
    fun `does not match ipv4 rule against ipv6 address`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.1"), 32)

        assertEquals(false, blocked.matches(InetAddress.getByName("2001:db8::1")))
    }

    @Test
    fun `matches ipv6 subnet`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::"), 32)

        assertEquals(true, blocked.matches(InetAddress.getByName("2001:db8:abcd::1")))
    }

    @Test
    fun `matches ipv4 subnet with partial byte`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 9)

        assertEquals(true, blocked.matches(InetAddress.getByName("10.0.0.1")))
    }

    @Test
    fun `does not match ipv4 subnet with partial byte`() {
        val blocked = ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 9)

        assertEquals(false, blocked.matches(InetAddress.getByName("10.128.0.1")))
    }

    @Test
    fun `contains address when any blocked client matches`() {
        val list =
            ClientBlockList(
                listOf(
                    ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 8),
                    ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::"), 32),
                ),
            )

        assertEquals(true, InetAddress.getByName("10.1.2.3") in list)
    }

    @Test
    fun `does not contain address when no blocked client matches`() {
        val list =
            ClientBlockList(
                listOf(
                    ClientBlockList.BlockedClient(InetAddress.getByName("10.0.0.0"), 8),
                    ClientBlockList.BlockedClient(InetAddress.getByName("2001:db8::"), 32),
                ),
            )

        assertEquals(false, InetAddress.getByName("192.168.1.1") in list)
    }
}
