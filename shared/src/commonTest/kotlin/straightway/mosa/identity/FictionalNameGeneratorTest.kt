package straightway.mosa.identity

import straightway.mosa.util.bitsRequiredForMax
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class FictionalNameGeneratorTest {
    @Test
    fun `smoke test`() {
        val sut = FictionalNameGenerator()
        val rng = Random(12345)
        val sampleIndices = (1..10000).map { rng.nextLong(sut.size) % sut.size }.distinct()
        val sampleNames = sampleIndices.map { sut[it] }

        assertEquals(sampleIndices.size, sampleNames.size, "No name duplicates")

        println("Overall size: ${formatNumber(sut.size)} Names (${bitsRequiredForMax(sut.size)} Bits)")
        val normalNamesCount = 50
        println((sampleNames.take(normalNamesCount)).joinToString("\n") { "- $it" })
        println((sampleNames.drop(normalNamesCount).filter(::hasDiphtong).take(5)).joinToString("\n") { "- $it" })
        println((sampleNames.drop(normalNamesCount).filter(::isShort).take(5)).joinToString("\n") { "- $it" })
    }

    private fun formatNumber(n: Long): String =
        n.toString().reversed().mapIndexed { i, c -> if ((i + 1) % 3 == 0) "_$c" else "$c" }
            .reversed().joinToString("").replace(Regex("^_"), "")

    private fun hasDiphtong(n: FictionalName): Boolean =
        Regex("[aeiou]{2}").find(n.toString()) != null
    private fun isShort(n: FictionalName): Boolean =
        n.sureName.count { it in setOf('a', 'e', 'i', 'o', 'u') } <= 2
}