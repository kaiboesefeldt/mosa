package straightway.mosa.identity

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FictionalNameGeneratorTest {
    @Test
    fun `smoke test`() {
        val sut = FictionalNameGenerator()
        val rng = Random(12345)
        val sampleIndices = (1..1000).map { rng.nextLong(sut.size) % sut.size }.distinct()
        val sampleNames = sampleIndices.map { sut[it] }
        assertEquals(sampleIndices.size, sampleNames.size)
    }
}