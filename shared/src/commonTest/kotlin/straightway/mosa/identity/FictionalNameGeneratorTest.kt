package straightway.mosa.identity

import straightway.mosa.util.BitStream
import kotlin.random.Random
import kotlin.test.Test

class FictionalNameGeneratorTest {
    @Test
    fun `smoke test`() {
        val sut = FictionalNameGenerator()
        val rng = Random(12345)
        val count = 1000
        val rndBits = BitStream(rng.nextBytes(32 * count))
        val sampleNames = (1..count).map { sut.generate(rndBits) }

        println("Overall size: ${sut.size} Names (${sut.bits} Bits)")
        val normalNamesCount = 50
        println((sampleNames.take(normalNamesCount)).joinToString("\n") { "- $it" })
    }
}
