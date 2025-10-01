package straightway.mosa.identity

import straightway.mosa.util.BitStream
import kotlin.random.Random
import kotlin.test.Test

class FictionalNameGeneratorTest {
    @Test
    fun `bit consumption test`() {
        val rng = Random(12345)
        val sut = FictionalNameGenerator()
        repeat(1000) {
            val bits = BitStream(rng.nextBytes(32), checkConsumption = true)
            sut.generate(bits) // Must not throw
        }
    }
}