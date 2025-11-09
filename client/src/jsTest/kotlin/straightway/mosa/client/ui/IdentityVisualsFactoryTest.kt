package straightway.mosa.client.ui

import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.identity.FictionalName
import straightway.mosa.util.BitStream

class IdentityVisualsFactoryTest {
    @Test
    fun `builds visuals from identity gid`() {
        val gidBytes = ByteArray(32) { index -> (index * 7 + 3).toByte() }
        val identity =
            Identity(
                gid = gidBytes.toBase64Url(),
                keyPair = emptyCryptoKeyPair(),
            )
        val name = FictionalName(givenNames = listOf("Nova"), sureName = "Rider")
        val builder = SteppedNameBuilder(name, consumedBits = 37)
        var avatarSeed: String? = null
        val factory =
            IdentityVisualsFactory(
                nameBuilder = builder::build,
                avatarBuilder = { seed ->
                    avatarSeed = seed
                    "avatar:$seed"
                },
            )

        val visuals = factory.create(identity)

        assertEquals(name, visuals.name)
        val expectedSeed = builder.expectedSeed(identity.gid)
        assertEquals(expectedSeed, avatarSeed)
        assertEquals("avatar:$expectedSeed", visuals.avatar)
    }
}

private class SteppedNameBuilder(
    val name: FictionalName,
    private val consumedBits: Int,
) {
    fun build(bits: BitStream): FictionalName {
        repeat(consumedBits) { bits.nextBit() }
        return name
    }

    fun expectedSeed(gid: String): String {
        val bytes = Base64.fromBase64Url(gid)
        val bits = BitStream(bytes)
        repeat(consumedBits) { bits.nextBit() }
        return bits.nextBytes(8).toBase64Url()
    }
}

private fun emptyCryptoKeyPair(): CryptoKeyPair = js("{}").unsafeCast<CryptoKeyPair>()
